package io.github.vgdotnet.dayboard.mining

private const val HEX = "0123456789abcdef"

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "odd hex length" }
    val out = ByteArray(length / 2)
    for (i in out.indices) out[i] = ((Character.digit(this[2 * i], 16) shl 4) or Character.digit(this[2 * i + 1], 16)).toByte()
    return out
}

fun ByteArray.toHex(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xff
        sb.append(HEX[v ushr 4]).append(HEX[v and 0xf])
    }
    return sb.toString()
}

fun ByteArray.putLe32(at: Int, v: Int) {
    this[at] = v.toByte()
    this[at + 1] = (v ushr 8).toByte()
    this[at + 2] = (v ushr 16).toByte()
    this[at + 3] = (v ushr 24).toByte()
}

fun ByteArray.getLe32(at: Int): Int =
    (this[at].toInt() and 0xff) or (this[at + 1].toInt() and 0xff shl 8) or (this[at + 2].toInt() and 0xff shl 16) or (this[at + 3].toInt() and 0xff shl 24)

fun Int.hex8(): String = String.format("%08x", this)
