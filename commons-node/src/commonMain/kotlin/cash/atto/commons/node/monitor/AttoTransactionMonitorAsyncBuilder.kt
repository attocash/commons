package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoFuture
import cash.atto.commons.AttoHeight
import cash.atto.commons.node.AttoNodeClientAsync
import cash.atto.commons.utils.JsExportForJs

@JsExportForJs
expect class AttoTransactionMonitorAsyncBuilder private constructor(
    nodeClient: AttoNodeClientAsync,
    accountMonitor: AttoAccountMonitorAsync,
) {
    fun heightProvider(value: (AttoAddress) -> AttoFuture<AttoHeight>): AttoTransactionMonitorAsyncBuilder

    fun build(): AttoTransactionMonitorAsync
}
