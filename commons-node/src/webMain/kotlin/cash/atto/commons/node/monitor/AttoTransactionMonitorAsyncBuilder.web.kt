package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.node.AttoFuture
import cash.atto.commons.node.AttoNodeClientAsync
import cash.atto.commons.node.await
import kotlinx.coroutines.Dispatchers

actual class AttoTransactionMonitorAsyncBuilder actual constructor(
    private val nodeClient: AttoNodeClientAsync,
    private val accountMonitor: AttoAccountMonitorAsync,
) {
    private var heightProvider: suspend (AttoAddress) -> AttoHeight = { AttoHeight.MIN }

    actual fun heightProvider(value: (AttoAddress) -> AttoFuture<AttoHeight>): AttoTransactionMonitorAsyncBuilder =
        apply {
            heightProvider = { value.invoke(it).await() }
        }

    actual fun build(): AttoTransactionMonitorAsync {
        val transactionMonitor = AttoTransactionMonitor(nodeClient.client, accountMonitor.monitor, heightProvider)
        return AttoTransactionMonitorAsync(transactionMonitor, Dispatchers.Default)
    }
}
