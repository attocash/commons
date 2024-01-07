package cash.atto.commons

fun ByteArray.toHex(): String {
    return joinToString("") { byte -> "%02X".format(byte) }
}

fun String.fromHexToByteArray(): ByteArray {
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun ByteArray.checkLength(size: Int) {
    require(this.size == size) { "Byte array contains ${this.size} characters but should contains $size" }
}