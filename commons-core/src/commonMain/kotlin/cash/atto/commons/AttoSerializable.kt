package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExportForJs
interface AttoSerializable {
    @OptIn(ExperimentalJsExport::class)
    @JsExport.Ignore
    fun toBuffer(): Buffer

    fun toByteArray(): ByteArray = toBuffer().readByteArray()
}
