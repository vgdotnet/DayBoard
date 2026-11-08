package io.github.vgdotnet.dayboard.mining

import android.util.Log

/**
 * JNI bridge to the arm64 kernel on the ARMv8 SHA-256 instructions (app/src/main/cpp/sha256_arm.c).
 * [isAvailable] is false when the library is missing or the CPU lacks the sha2 extension; the engine
 * then uses [Sha256Kernel].
 */
object NativeKernel {
    val isAvailable: Boolean by lazy {
        runCatching {
            System.loadLibrary("dayboardminer")
            available()
        }.onFailure { Log.w("NativeKernel", "not available: ${it.message}") }.getOrDefault(false)
    }

    @JvmStatic private external fun available(): Boolean

    /** Prepares the midstates of [versions] 80-byte headers laid out back to back; returns a handle. */
    @JvmStatic external fun prepare(headers: ByteArray, versions: Int): Long

    @JvmStatic external fun release(handle: Long)

    /** Scans [count] W3 words from [startW]; fills [out] with (word, version) pairs; returns the pair count. */
    @JvmStatic external fun scan(handle: Long, startW: Int, count: Int, out: IntArray): Int
}
