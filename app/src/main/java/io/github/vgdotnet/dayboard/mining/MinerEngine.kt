package io.github.vgdotnet.dayboard.mining

import android.os.Process
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * The scanning threads. Each worker takes 65536-nonce chunks of the current job, runs the kernel
 * for every header version and verifies hits with the reference digest. Shares above the pool's
 * threshold go to [takeShare]; the Stratum client submits them.
 */
class MinerEngine(private val threads: Int, private val useNative: Boolean) {
    @Volatile private var job: Job? = null
    @Volatile private var jobSeq = 0
    @Volatile private var running = false
    @Volatile var paused = false
    @Volatile var shareDifficulty = 1.0

    val hashes = AtomicLong(0)
    private val shares = ConcurrentLinkedQueue<Share>()
    private val workers = ArrayList<Thread>()
    private val lock = Any()
    @Volatile var best = 0.0
        private set

    /** Native handles of replaced jobs; freed one job later, when no worker can still be scanning them. */
    private val retired = ArrayDeque<Long>()

    fun setJob(j: Job?) {
        synchronized(lock) {
            val old = job
            job = j
            jobSeq = j?.seq ?: 0
            if (j == null) shares.clear() // shares of a dropped connection cannot be submitted to the new one
            while (retired.size > 1) NativeKernel.release(retired.removeFirst())
            if (old != null && old.nativeHandle != 0L) retired.addLast(old.nativeHandle)
        }
    }

    fun takeShare(): Share? = shares.poll()

    fun start() {
        if (running) return
        running = true
        for (i in 0 until threads) {
            val t = Thread({ worker(i) }, "miner-$i")
            t.isDaemon = true
            t.start()
            workers += t
        }
    }

    fun stop() {
        running = false
        workers.forEach { it.interrupt() }
        workers.forEach { runCatching { it.join(1_000) } }
        workers.clear()
        synchronized(lock) {
            job?.nativeHandle?.takeIf { it != 0L }?.let { retired.addLast(it) }
            job = null
            while (retired.isNotEmpty()) NativeKernel.release(retired.removeFirst())
        }
    }

    private fun worker(index: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        val w = IntArray(64)
        val x = IntArray(61)
        val out = IntArray(128)
        while (running) {
            val j = job
            if (j == null || paused) {
                sleepQuietly(100)
                continue
            }
            val chunk = j.nextChunk.getAndIncrement()
            if (chunk >= 65536) {
                // Nonce space exhausted for this job: wait for the next one
                while (running && jobSeq == j.seq) sleepQuietly(50)
                continue
            }
            val sw = j.sw
            val seq = j.seq
            var word = chunk shl 16
            var done = 0
            val native = useNative && j.nativeHandle != 0L
            while (done < 65536 && running) {
                // Check for a new job every 4096 nonces (a few milliseconds)
                if (jobSeq != seq) break
                if (native) {
                    val n = NativeKernel.scan(j.nativeHandle, word, 4096, out)
                    for (k in 0 until n) onHit(j, Integer.reverseBytes(out[2 * k]), out[2 * k + 1])
                    word += 4096
                } else {
                    for (i in 0 until 4096) {
                        val nonce = Integer.reverseBytes(word)
                        val hits = Sha256Kernel.scan(sw, nonce, w, x)
                        if (hits != 0) onHits(j, nonce, hits)
                        word++
                    }
                }
                done += 4096
                hashes.addAndGet(4096L * j.versions)
            }
        }
    }

    private fun onHits(j: Job, nonce: Int, hitsIn: Int) {
        var hits = hitsIn
        while (hits != 0) {
            val version = Integer.numberOfTrailingZeros(hits)
            hits = hits and (hits - 1)
            onHit(j, nonce, version)
        }
    }

    /** Full hash of a kernel hit with the reference digest: a check of the fast kernel as well. */
    private fun onHit(j: Job, nonce: Int, version: Int) {
        if (version !in 0 until j.versions) return
        val hash = PoW.sha256d(j.headerFor(version, nonce))
        if (hash[31].toInt() != 0 || hash[30].toInt() != 0) {
            Log.w(TAG, "kernel hit without zero bits: nonce ${nonce.hex8()} version $version")
            return
        }
        candidate(j, version, nonce, hash)
    }

    /** A hash with 16 leading zero bits: best hash, block, share. */
    private fun candidate(j: Job, version: Int, nonce: Int, hash: ByteArray) {
        val difficulty = PoW.hashDifficulty(hash)
        synchronized(lock) { if (difficulty > best) best = difficulty }
        MinerRuntime.update { if (difficulty > it.best) it.copy(best = difficulty) else it }
        if (PoW.hashMeetsBits(hash, j.nbits)) {
            MinerRuntime.update { it.copy(blocks = it.blocks + 1) }
            Log.i(TAG, "!!! BLOCK: job ${j.id} nonce ${nonce.hex8()} version bits ${j.versionBits[version].hex8()}")
        }
        if (difficulty >= shareDifficulty) {
            if (shares.size < 16) shares.add(Share(j.id, j.extranonce2, j.ntime, nonce, j.versionBits[version], difficulty))
        }
    }

    private fun sleepQuietly(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) { }
    }

    private companion object {
        const val TAG = "MinerEngine"
    }
}
