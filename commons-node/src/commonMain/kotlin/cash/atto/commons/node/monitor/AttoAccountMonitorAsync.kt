package cash.atto.commons.node.monitor

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher

@JsExportForJs
expect class AttoAccountMonitorAsync internal constructor(
    monitor: AttoAccountMonitor,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    val monitor: AttoAccountMonitor

    override fun close()
}

fun AttoAccountMonitor.toAsync(dispatcher: CoroutineDispatcher): AttoAccountMonitorAsync = AttoAccountMonitorAsync(this, dispatcher)
