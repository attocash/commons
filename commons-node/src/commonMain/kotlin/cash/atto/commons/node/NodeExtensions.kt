@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons.node

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.toBuffer
import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.json.Json
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsName

private val json =
    Json {
        ignoreUnknownKeys = true
    }

@JsExportForJs
@JsName("receivableToJson")
fun AttoReceivable.toJson(): String = json.encodeToString(this)

@JsExportForJs
@JsName("receivableFromJson")
fun String.toReceivable(): AttoReceivable = json.decodeFromString<AttoReceivable>(this)

@JsExportForJs
@JsName("transactionToJson")
fun AttoTransaction.toJson(): String = json.encodeToString(this)

@JsExportForJs
@JsName("transactionFromJson")
fun String.toTransaction(): AttoTransaction = json.decodeFromString<AttoTransaction>(this)

@JsExportForJs
@JsName("blockToJson")
fun AttoBlock.toJson(): String = json.encodeToString(this)

@JsExportForJs
@JsName("blockFromJson")
fun String.toBlock(): AttoBlock = json.decodeFromString<AttoBlock>(this)

@JsExportForJs
@JsName("fromByteArrayToTransactionJson")
fun ByteArray.toTransactionJson(): String {
    val transaction = AttoTransaction.fromBuffer(this.toBuffer()) ?: throw IllegalArgumentException("Invalid transaction")
    return json.encodeToString<AttoTransaction>(transaction)
}

@JsExportForJs
@JsName("fromByteArrayToBlockJson")
fun ByteArray.toBlockJson(): String {
    val block = AttoBlock.fromBuffer(this.toBuffer()) ?: throw IllegalArgumentException("Invalid block")
    return json.encodeToString<AttoBlock>(block)
}
