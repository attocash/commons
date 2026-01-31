package cash.atto.commons.node.monitor

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher

@JsExportForJs
expect class AttoTransactionMonitorAsync internal constructor(
    transactionMonitor: AttoTransactionMonitor,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    val transactionMonitor: AttoTransactionMonitor

    override fun close()
}

fun AttoTransactionMonitor.toAsync(dispatcher: CoroutineDispatcher): AttoTransactionMonitorAsync =
    AttoTransactionMonitorAsync(this, dispatcher)
