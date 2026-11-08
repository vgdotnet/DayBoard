package io.github.vgdotnet.dayboard.mining

import android.util.Log
import io.github.vgdotnet.dayboard.model.MinerSettings
import java.io.OutputStreamWriter
import java.io.Writer
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stratum v1 client: connects to the pool, turns mining.notify into jobs for the engine, submits
 * its shares. Runs on its own thread and reconnects when the pool drops, goes silent or does not
 * answer a share. A port of the ESP32 miner's stratum.cpp.
 */
class StratumClient(private val settings: MinerSettings, private val engine: MinerEngine, private val useNative: Boolean) {
    @Volatile private var running = false
    private var thread: Thread? = null
    @Volatile private var socket: Socket? = null

    private var extranonce1 = ""
    private var extranonce2Size = 4
    private var versionMask = 0
    private var jobCounter = 0

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "stratum").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        thread?.interrupt()
        thread = null
    }

    private fun loop() {
        var failures = 0
        while (running) {
            MinerRuntime.status("pool...")
            val s = Socket()
            socket = s
            try {
                s.connect(InetSocketAddress(settings.host, settings.port), 10_000)
                s.soTimeout = 500
                Log.i(TAG, "connected to ${settings.host}:${settings.port}")
                failures = 0
                MinerRuntime.update { it.copy(poolConnected = true) }
                session(s)
            } catch (e: Exception) {
                if (running) {
                    Log.w(TAG, "pool: ${e.message}")
                    MinerRuntime.status("pool down")
                    failures++
                }
            } finally {
                runCatching { s.close() }
                socket = null
                MinerRuntime.update { it.copy(poolConnected = false) }
                // Jobs of the old connection cannot be submitted: the new one gets another extranonce1
                engine.setJob(null)
            }
            if (!running) break
            try { Thread.sleep(if (failures > 0) minOf(2_000L * failures, 30_000L) else 2_000L) } catch (_: InterruptedException) { }
        }
    }

    private fun send(out: Writer, line: String) {
        Log.d(TAG, "> ${line.trimEnd()}")
        synchronized(out) {
            out.write(line)
            out.flush()
        }
    }

    private fun session(s: Socket) {
        extranonce1 = ""
        versionMask = 0
        val input = s.getInputStream()
        val out = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
        val user = "${settings.wallet}.${settings.worker}"
        MinerRuntime.status("subscribing")
        send(out, message(3, "mining.configure", JSONArray().put(JSONArray().put("version-rolling")).put(
            JSONObject().put("version-rolling.mask", VERSION_MASK.hex8()).put("version-rolling.min-bit-count", 2))))
        send(out, message(1, "mining.subscribe", JSONArray().put("DayBoard-Android/0.1")))
        send(out, message(2, "mining.authorize", JSONArray().put(user).put(settings.password)))
        send(out, message(4, "mining.suggest_difficulty", JSONArray().put(settings.suggestDifficulty)))

        val pending = HashMap<Int, Long>() // id of a submitted share -> time sent
        var nextId = 10
        var lastData = System.currentTimeMillis()
        // Bytes are accumulated here and split on newlines: a read timeout loses nothing, and a
        // read of -1 is the pool closing the connection (the protocol is ASCII JSON lines)
        val buf = ByteArray(8192)
        val acc = StringBuilder()
        while (running && !s.isClosed) {
            val n = try {
                input.read(buf)
            } catch (_: SocketTimeoutException) {
                0
            }
            if (n < 0) {
                MinerRuntime.status("pool closed")
                return
            }
            if (n > 0) {
                lastData = System.currentTimeMillis()
                acc.append(String(buf, 0, n, Charsets.ISO_8859_1))
                while (true) {
                    val eol = acc.indexOf("\n")
                    if (eol < 0) break
                    val line = acc.substring(0, eol).trimEnd('\r')
                    acc.delete(0, eol + 1)
                    if (!handleLine(line, pending)) return
                }
                if (acc.length > 1 shl 20) acc.setLength(0) // a megabyte without a newline is not Stratum
            }

            var share = engine.takeShare()
            while (share != null) {
                val params = JSONArray().put(user).put(share.jobId).put(share.extranonce2).put(share.ntime).put(share.nonce.hex8())
                if (share.versionBits != 0) params.put(share.versionBits.hex8())
                pending[nextId] = System.currentTimeMillis()
                Log.i(TAG, "share difficulty ${"%.3f".format(share.difficulty)}")
                send(out, message(nextId++, "mining.submit", params))
                share = engine.takeShare()
            }

            val now = System.currentTimeMillis()
            if (pending.values.any { now - it > ANSWER_TIMEOUT_MS }) {
                MinerRuntime.status("no answer")
                return
            }
            if (now - lastData > SILENCE_TIMEOUT_MS) {
                MinerRuntime.status("pool silent")
                return
            }
        }
    }

    private fun message(id: Int, method: String, params: JSONArray): String =
        JSONObject().put("id", id).put("method", method).put("params", params).toString() + "\n"

    /** One line from the pool; false means the connection must be closed. */
    private fun handleLine(line: String, pending: MutableMap<Int, Long>): Boolean {
        val doc = runCatching { JSONObject(line) }.getOrNull() ?: return true
        val method = doc.optString("method", "")
        if (method.isNotEmpty()) {
            when (method) {
                "mining.notify" -> buildJob(doc.getJSONArray("params"))
                "mining.set_difficulty" -> {
                    val d = doc.getJSONArray("params").getDouble(0)
                    engine.shareDifficulty = d
                    MinerRuntime.update { it.copy(shareDifficulty = d) }
                    Log.i(TAG, "share difficulty $d")
                }
                "mining.set_version_mask" -> {
                    versionMask = parseHex(doc.getJSONArray("params").getString(0)) and VERSION_MASK
                    MinerRuntime.update { it.copy(versionMask = versionMask) }
                }
            }
            return true
        }
        val id = doc.optInt("id", -1)
        val ok = doc.isNull("error") && doc.optBoolean("result", false)
        when (id) {
            1 -> { // subscribe
                val result = doc.optJSONArray("result") ?: return false
                extranonce1 = result.getString(1)
                extranonce2Size = result.optInt(2, 4)
            }
            2 -> MinerRuntime.status(if (ok) "mining" else "auth failed") // authorize
            3 -> { // configure: version rolling
                val result = doc.optJSONObject("result")
                if (result != null && result.optBoolean("version-rolling", false)) {
                    versionMask = parseHex(result.optString("version-rolling.mask", "0")) and VERSION_MASK
                }
                MinerRuntime.update { it.copy(versionMask = versionMask) }
                Log.i(TAG, "version rolling mask ${versionMask.hex8()}")
            }
            else -> if (id >= 10 && pending.remove(id) != null) {
                MinerRuntime.update {
                    if (ok) it.copy(accepted = it.accepted + 1, lastShareAt = System.currentTimeMillis()) else it.copy(rejected = it.rejected + 1)
                }
                Log.i(TAG, "share ${if (ok) "accepted" else "rejected"}${if (!ok) ": " + doc.opt("error") else ""}")
            }
        }
        return true
    }

    private fun buildJob(params: JSONArray) {
        if (extranonce1.isEmpty()) return
        val id = params.getString(0)
        val prevHex = params.getString(1)
        val coinb1 = params.getString(2)
        val coinb2 = params.getString(3)
        val branches = params.getJSONArray(4)
        val version = parseHex(params.getString(5))
        val nbits = parseHex(params.getString(6))
        val ntime = params.getString(7)
        val extranonce2 = "00".repeat(extranonce2Size)

        // coinbase = coinb1 + extranonce1 + extranonce2 + coinb2; extranonce2 is zero for every job:
        // jobs differ by job_id and connections by extranonce1
        val coinbase = (coinb1 + extranonce1 + extranonce2 + coinb2).hexToBytes()
        val height = coinbaseHeight(coinbase)
        var root = PoW.sha256d(coinbase)
        for (i in 0 until branches.length()) {
            root = PoW.sha256d(root + branches.getString(i).hexToBytes())
        }

        val h = ByteArray(80)
        h.putLe32(0, version)
        val prev = prevHex.hexToBytes()
        for (i in 0 until 8) for (k in 0 until 4) h[4 + 4 * i + k] = prev[4 * i + 3 - k] // Stratum sends prev hash with bytes swapped in each word
        System.arraycopy(root, 0, h, 36, 32)
        h.putLe32(68, parseHex(ntime))
        h.putLe32(72, nbits)
        h.putLe32(76, 0)

        val versions = if (Integer.bitCount(versionMask) >= 3) Sha256Kernel.MAX_VERSIONS else 1
        val bits = IntArray(versions) { spreadBits(versionMask, it) }
        val headers = (0 until versions).map { v ->
            h.copyOf().also { it.putLe32(0, version xor bits[v]) }
        }
        val handle = if (useNative) NativeKernel.prepare(ByteArray(80 * versions).also { all -> headers.forEachIndexed { i, hv -> System.arraycopy(hv, 0, all, 80 * i, 80) } }, versions) else 0L
        val job = Job(++jobCounter, id, ntime, extranonce2, nbits, height, bits, h, Sha256Kernel.SwJob(headers), handle)
        MinerRuntime.update { it.copy(height = height, netDifficulty = PoW.bitsDifficulty(nbits)) }
        engine.setJob(job)
    }

    private companion object {
        const val TAG = "Stratum"
        const val VERSION_MASK = 0x1fffe000
        // The pool answers a share within a fraction of a second; no answer means a dead connection
        const val ANSWER_TIMEOUT_MS = 15_000L
        // Jobs arrive at least every minute or two
        const val SILENCE_TIMEOUT_MS = 300_000L

        fun parseHex(s: String): Int = java.lang.Long.parseLong(s.removePrefix("0x"), 16).toInt()

        /** Spreads the bits of [v] over the set bits of [mask], lowest first: version number v. */
        fun spreadBits(maskIn: Int, vIn: Int): Int {
            var mask = maskIn
            var v = vIn
            var bits = 0
            while (mask != 0 && v != 0) {
                if (v and 1 != 0) bits = bits or (mask and -mask)
                mask = mask and (mask - 1)
                v = v ushr 1
            }
            return bits
        }

        /** Block height from the coinbase (BIP34): the first push of scriptSig. */
        fun coinbaseHeight(cb: ByteArray): Long {
            var p = 4
            if (cb.size > p + 1 && cb[p].toInt() == 0 && cb[p + 1].toInt() == 1) p += 2 // witness marker
            p += 1 + 36 + 1 // input count, outpoint, scriptSig length
            if (p >= cb.size) return -1
            val n = cb[p++].toInt() and 0xff
            if (n < 1 || n > 8 || p + n > cb.size) return -1
            var height = 0L
            for (i in n - 1 downTo 0) height = (height shl 8) or (cb[p + i].toLong() and 0xff)
            return height
        }
    }
}
