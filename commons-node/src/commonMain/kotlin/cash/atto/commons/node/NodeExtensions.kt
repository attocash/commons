@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons.node

import cash.atto.commons.AttoBlock
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

@JsExportForJs
@JsName("receivableToJson")
fun AttoReceivable.toJson(): String = Json.encodeToString(this)

@JsExportForJs
@JsName("receivableFromJson")
fun String.toReceivable(): AttoReceivable = Json.decodeFromString<AttoReceivable>(this)

@JsExportForJs
@JsName("transactionToJson")
fun AttoTransaction.toJson(): String = Json.encodeToString(this)

@JsExportForJs
@JsName("transactionFromJson")
fun String.toTransaction(): AttoTransaction = Json.decodeFromString<AttoTransaction>(this)

@JsExportForJs
@JsName("blockToJson")
fun AttoBlock.toJson(): String = Json.encodeToString(this)

@JsExportForJs
@JsName("blockFromJson")
fun String.toBlock(): AttoBlock = Json.decodeFromString<AttoBlock>(this)

@JsExportForJs
@JsName("fromByteArrayToTransactionJson")
fun ByteArray.toTransactionJson(): String {
    val transaction = AttoTransaction.fromBuffer(this.toBuffer()) ?: throw IllegalArgumentException("Invalid transaction")
    return Json.encodeToString<AttoTransaction>(transaction)
}

@JsExportForJs
@JsName("fromByteArrayToBlockJson")
fun ByteArray.toBlockJson(): String {
    val block = AttoBlock.fromBuffer(this.toBuffer()) ?: throw IllegalArgumentException("Invalid block")
    return Json.encodeToString<AttoBlock>(block)
}

@OptIn(ExperimentalMultiplatform::class)
@JsExportForJs
expect class AttoFuture<T>

expect fun <T> CoroutineScope.submit(block: suspend () -> T): AttoFuture<T>

expect suspend fun <T> AttoFuture<T>.await(): T

internal inline fun <T> CoroutineScope.consumeStream(
    stream: Flow<T>,
    crossinline onEach: suspend (T) -> Unit,
    noinline onCancel: (Exception?) -> Unit,
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
