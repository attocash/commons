package cash.atto.commons.node.monitor

import cash.atto.commons.node.AttoNodeClientAsync
import cash.atto.commons.utils.JsExportForJs

@JsExportForJs
expect class AttoTransactionMonitorAsyncBuilder private constructor(
    nodeClient: AttoNodeClientAsync,
    accountMonitor: AttoAccountMonitorAsync,
) {
    fun build(): AttoTransactionMonitorAsync
}
