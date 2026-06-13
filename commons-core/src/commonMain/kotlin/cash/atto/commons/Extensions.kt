package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readULongLe
import kotlinx.io.writeULongLe
import kotlin.js.ExperimentalJsExport

@OptIn(ExperimentalStdlibApi::class, ExperimentalJsExport::class)
@JsExportForJs
fun ByteArray.toHex(): String = this.toHexString(HexFormat.UpperCase)

fun Buffer.toHex(): String = this.copy().readByteArray().toHex()

fun AttoSerializable.toHex(): String = toBuffer().toHex()

@OptIn(ExperimentalJsExport::class, ExperimentalStdlibApi::class)
@JsExportForJs
fun String.fromHexToByteArray(): ByteArray {
    require(length % 2 == 0) { "Hex string must have an even number of characters" }
    return hexToByteArray()
}

internal fun String.fromHexToByteArray(size: Int): ByteArray {
    val bytes = fromHexToByteArray()
    require(bytes.size == size) { "Hex string contains $length characters but should contain ${size * 2}" }
    return bytes
}

internal fun String.fromHexToByteArrayAtLeast(size: Int): ByteArray {
    val bytes = fromHexToByteArray()
    require(bytes.size >= size) { "Hex string contains $length characters but should contain at least ${size * 2}" }
    return bytes
}

fun ByteArray.checkLength(size: Int) {
    require(this.size == size) { "Byte array contains ${this.size} characters but should contains $size" }
}

fun AttoInstant.toByteArray(): ByteArray {
    val buffer = Buffer()
    buffer.writeInstant(this)
    return buffer.readByteArray()
}

fun ByteArray.toULong(): ULong {
    val buffer = Buffer()
    buffer.write(this)
    return buffer.readULongLe()
}

fun ULong.toByteArray(): ByteArray {
    val buffer = Buffer()
    buffer.writeULongLe(this)
    return buffer.readByteArray()
}
