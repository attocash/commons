package cash.atto.commons.node.monitor

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher

@JsExportForJs
expect class AttoAccountEntryMonitorAsync internal constructor(
    accountEntryMonitor: AttoAccountEntryMonitor,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    override fun close()
}

fun AttoAccountEntryMonitor.toAsync(dispatcher: CoroutineDispatcher): AttoAccountEntryMonitorAsync =
    AttoAccountEntryMonitorAsync(this, dispatcher)
