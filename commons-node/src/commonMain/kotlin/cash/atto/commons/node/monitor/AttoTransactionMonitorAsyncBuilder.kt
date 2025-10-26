package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.node.AttoNodeClientAsync

expect class AttoTransactionMonitorAsyncBuilder private constructor(
    nodeClient: AttoNodeClientAsync,
    accountMonitor: AttoAccountMonitorAsync,
) {
    var heightProvider: (AttoAddress) -> AttoHeight

    fun heightProvider(value: (AttoAddress) -> AttoHeight): AttoTransactionMonitorAsyncBuilder

    fun build(): AttoTransactionMonitorAsync
}
