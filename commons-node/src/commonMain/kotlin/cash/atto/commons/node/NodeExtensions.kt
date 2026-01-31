@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons.node

import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoJob
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.toBuffer
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
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
@JsName("accountEntryToJson")
fun AttoAccountEntry.toJson(): String = json.encodeToString(this)

@JsExportForJs
@JsName("accountEntryFromJson")
fun String.toAccountEntry(): AttoAccountEntry = json.decodeFromString<AttoAccountEntry>(this)

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

internal inline fun <T> CoroutineScope.consumeStream(
    stream: Flow<T>,
    crossinline onEach: suspend (T) -> Unit,
    noinline onCancel: suspend (Exception?) -> Unit,
): AttoJob =
    AttoJob(
        launch {
            try {
                stream.collect { onEach(it) }
                onCancel(null)
            } catch (e: CancellationException) {
                onCancel(null)
                throw e
            } catch (e: Exception) {
                onCancel(e)
            }
        },
    )
