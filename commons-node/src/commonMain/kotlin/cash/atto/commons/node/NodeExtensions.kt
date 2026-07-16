@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoJob
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsName

private const val CORE_JSON_DEPRECATION = "Moved to commons-core; will be removed in 8.0.0"

@JsExportForJs
@JsName("accountToJson")
@Deprecated(CORE_JSON_DEPRECATION, ReplaceWith("this.toJson()"))
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoAccount.toJson(): String = this.toJson()

@JsExportForJs
@JsName("accountFromJson")
@Deprecated(CORE_JSON_DEPRECATION, ReplaceWith("AttoAccount.fromJson(this)", "cash.atto.commons.AttoAccount"))
fun String.toAccount(): AttoAccount = AttoAccount.fromJson(this)

@JsExportForJs
@JsName("receivableToJson")
@Deprecated(CORE_JSON_DEPRECATION, ReplaceWith("this.toJson()"))
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoReceivable.toJson(): String = this.toJson()

@JsExportForJs
@JsName("receivableFromJson")
@Deprecated(CORE_JSON_DEPRECATION, ReplaceWith("AttoReceivable.fromJson(this)", "cash.atto.commons.AttoReceivable"))
fun String.toReceivable(): AttoReceivable = AttoReceivable.fromJson(this)

@JsExportForJs
@JsName("transactionToJson")
@Deprecated(CORE_JSON_DEPRECATION, ReplaceWith("this.toJson()"))
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoTransaction.toJson(): String = this.toJson()

@JsExportForJs
@JsName("transactionFromJson")
@Deprecated(CORE_JSON_DEPRECATION, ReplaceWith("AttoTransaction.fromJson(this)", "cash.atto.commons.AttoTransaction"))
fun String.toTransaction(): AttoTransaction = AttoTransaction.fromJson(this)

@JsExportForJs
@JsName("blockToJson")
@Deprecated(CORE_JSON_DEPRECATION, ReplaceWith("this.toJson()"))
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoBlock.toJson(): String = this.toJson()

@JsExportForJs
@JsName("blockFromJson")
@Deprecated(CORE_JSON_DEPRECATION, ReplaceWith("AttoBlock.fromJson(this)", "cash.atto.commons.AttoBlock"))
fun String.toBlock(): AttoBlock = AttoBlock.fromJson(this)

@JsExportForJs
@JsName("accountEntryToJson")
@Deprecated(CORE_JSON_DEPRECATION, ReplaceWith("this.toJson()"))
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoAccountEntry.toJson(): String = this.toJson()

@JsExportForJs
@JsName("accountEntryFromJson")
@Deprecated(CORE_JSON_DEPRECATION, ReplaceWith("AttoAccountEntry.fromJson(this)", "cash.atto.commons.AttoAccountEntry"))
fun String.toAccountEntry(): AttoAccountEntry = AttoAccountEntry.fromJson(this)

@JsExportForJs
@JsName("fromByteArrayToTransactionJson")
@Deprecated(
    CORE_JSON_DEPRECATION,
    ReplaceWith("AttoTransaction.fromByteArray(this).toJson()", "cash.atto.commons.AttoTransaction"),
)
fun ByteArray.toTransactionJson(): String = AttoTransaction.fromByteArray(this).toJson()

@JsExportForJs
@JsName("fromByteArrayToBlockJson")
@Deprecated(CORE_JSON_DEPRECATION, ReplaceWith("AttoBlock.fromByteArray(this).toJson()", "cash.atto.commons.AttoBlock"))
fun ByteArray.toBlockJson(): String = AttoBlock.fromByteArray(this).toJson()

internal inline fun <T> CoroutineScope.consumeStream(
    stream: Flow<T>,
    crossinline onEach: suspend (T) -> Unit,
    noinline onCancel: suspend (Exception?) -> Unit,
): AttoJob =
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
    }.toAttoJob()

internal fun Job.toAttoJob(): AttoJob =
    AttoJob.create(
        activeProvider = { isActive },
        cancellation = { cancel() },
    )
