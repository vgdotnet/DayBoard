package io.github.vgdotnet.dayboard.mining

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/** A pool job prepared for scanning. Headers of all versions share everything but the version word. */
class Job(
    /** Miner-side sequence number: workers compare it and drop a stale job. */
    val seq: Int,
    val id: String,
    /** ntime as the pool sent it (hex). */
    val ntime: String,
    val extranonce2: String,
    val nbits: Int,
    val height: Long,
    /** Version-rolling bits of each version; index 0 is the job's own version (bits 0). */
    val versionBits: IntArray,
    /** 80-byte header of the job's version with nonce 0. */
    val header: ByteArray,
    val sw: Sha256Kernel.SwJob,
    /** Handle of the native kernel's prepared job, 0 when the Kotlin kernel is used. */
    val nativeHandle: Long = 0,
) {
    val versions: Int get() = versionBits.size

    /** Next 65536-nonce chunk to scan; workers take chunks from here. */
    val nextChunk = AtomicInteger(0)

    fun headerFor(version: Int, nonce: Int): ByteArray {
        val out = header.copyOf()
        out.putLe32(0, out.getLe32(0) xor versionBits[version])
        out.putLe32(76, nonce)
        return out
    }
}

/** A share to submit to the pool. */
data class Share(
    val jobId: String,
    val extranonce2: String,
    val ntime: String,
    val nonce: Int,
    val versionBits: Int,
    val difficulty: Double,
)

object PoW {
    private val digest = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }

    /** Double SHA-256 of arbitrary data: coinbase, merkle branches, verification of kernel hits. */
    fun sha256d(data: ByteArray): ByteArray {
        val d = digest.get()!!
        d.reset()
        return d.digest(d.digest(data))
    }

    /** Difficulty 1 is the target 0xffff * 2^208. */
    private val DIFF1 = 65535.0 * Math.pow(2.0, 208.0)

    /** The hash as a little-endian number, difficulty = diff1 target / value. */
    fun hashDifficulty(hash: ByteArray): Double {
        var value = 0.0
        for (i in 31 downTo 0) value = value * 256.0 + (hash[i].toInt() and 0xff)
        return if (value > 0) DIFF1 / value else 0.0
    }

    fun hashMeetsBits(hash: ByteArray, nbits: Int): Boolean {
        val target = ByteArray(32)
        val exp = nbits ushr 24
        for (i in 0 until 3) {
            val at = exp - 3 + i
            if (at in 0..31) target[at] = (nbits ushr (8 * i)).toByte()
        }
        for (i in 31 downTo 0) {
            val h = hash[i].toInt() and 0xff
            val t = target[i].toInt() and 0xff
            if (h != t) return h < t
        }
        return true
    }

    fun bitsDifficulty(nbits: Int): Double {
        val mantissa = (nbits and 0xffffff).toDouble()
        return if (mantissa > 0) 65535.0 / mantissa * Math.pow(256.0, (0x1d - (nbits ushr 24)).toDouble()) else 0.0
    }

    // Genesis block: the header with nonce 0x7c2bac1d hashes to 000000000019d668...
    private const val GENESIS_HEADER =
        "0100000000000000000000000000000000000000000000000000000000000000000000003ba3edfd7a7b12b27ac72c3e67768f61" +
            "7fc81bc3888a51323a9fb8aa4b1e5e4a29ab5f49ffff001d1dac2b7c"
    private const val GENESIS_NONCE = 0x7c2bac1d
    private const val GENESIS_HASH = "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"

    /** Reference digest and the kernel against the genesis block; false means mining must not start. */
    fun selfTest(): Boolean {
        val genesis = GENESIS_HEADER.hexToBytes()
        val expected = GENESIS_HASH.hexToBytes().reversedArray()
        val refOk = sha256d(genesis).contentEquals(expected)
        val sw = Sha256Kernel.SwJob(listOf(genesis))
        val w = IntArray(64)
        val x = IntArray(61)
        val swOk = (Sha256Kernel.scan(sw, GENESIS_NONCE, w, x) and 1) != 0 && (Sha256Kernel.scan(sw, GENESIS_NONCE + 1, w, x) and 1) == 0
        return refOk && swOk
    }

    /** The native kernel must find the genesis nonce (and nothing else) in 16 words around it. */
    fun nativeSelfTest(): Boolean {
        if (!NativeKernel.isAvailable) return false
        val genesis = GENESIS_HEADER.hexToBytes()
        val handle = NativeKernel.prepare(genesis, 1)
        if (handle == 0L) return false
        try {
            val word = Integer.reverseBytes(GENESIS_NONCE)
            val out = IntArray(16)
            val n = NativeKernel.scan(handle, word - 8, 16, out)
            return n == 1 && out[0] == word && out[1] == 0
        } finally {
            NativeKernel.release(handle)
        }
    }
}
