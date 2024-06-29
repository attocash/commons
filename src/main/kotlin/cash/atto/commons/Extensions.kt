package cash.atto.commons

import kotlinx.datetime.Instant
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readULongLe

@OptIn(ExperimentalStdlibApi::class)
fun ByteArray.toHex(): String = this.toHexString(HexFormat.UpperCase)

fun Buffer.toHex(): String = this.copy().readByteArray().toHex()

fun String.fromHexToByteArray(): ByteArray =
    chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

fun ByteArray.checkLength(size: Int) {
    require(this.size == size) { "Byte array contains ${this.size} characters but should contains $size" }
}

fun Instant.toByteArray(): ByteArray {
    val buffer = Buffer()
    buffer.writeInstant(this)
    return buffer.readByteArray()
}

fun ByteArray.toULong(): ULong {
    val buffer = Buffer()
    buffer.write(this)
    return buffer.readULongLe()
}
