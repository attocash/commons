package cash.atto.commons.node.monitor

import cash.atto.commons.AttoJob
import cash.atto.commons.AttoTransaction
import cash.atto.commons.node.consumeStream
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.js.ExperimentalJsExport

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
actual class AttoTransactionMonitorAsync internal actual constructor(
    actual val transactionMonitor: AttoTransactionMonitor,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun onTransaction(
        onTransaction: suspend (AttoTransaction) -> Unit,
        onCancel: (Exception?) -> Unit,
    ): AttoJob =
        scope.consumeStream(
            stream = transactionMonitor.stream(),
            onEach = {
                onTransaction(it.value)
                it.acknowledge()
            },
            onCancel = { onCancel.invoke(it) },
        )

    actual override fun close() {
        scope.cancel()
    }
}

fun AttoTransactionMonitor.toAsync(): AttoTransactionMonitorAsync = AttoTransactionMonitorAsync(this, Dispatchers.Default)
