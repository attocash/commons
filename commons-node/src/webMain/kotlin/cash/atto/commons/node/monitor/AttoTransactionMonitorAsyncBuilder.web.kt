package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.node.AttoNodeClientAsync
import kotlinx.coroutines.Dispatchers

actual class AttoTransactionMonitorAsyncBuilder actual constructor(
    private val nodeClient: AttoNodeClientAsync,
    private val accountMonitor: AttoAccountMonitorAsync,
) {
    actual var heightProvider: (AttoAddress) -> AttoHeight = { AttoHeight.MIN }

    actual fun heightProvider(value: (AttoAddress) -> AttoHeight): AttoTransactionMonitorAsyncBuilder =
        apply {
            heightProvider = value
        }

    actual fun build(): AttoTransactionMonitorAsync {
        val transactionMonitor = AttoTransactionMonitor(nodeClient.client, accountMonitor.monitor, heightProvider)
        return AttoTransactionMonitorAsync(transactionMonitor, Dispatchers.Default)
    }
}
