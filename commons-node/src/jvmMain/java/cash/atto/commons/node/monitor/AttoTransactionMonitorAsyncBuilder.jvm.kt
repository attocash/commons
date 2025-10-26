package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.node.AttoNodeClientAsync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService

actual class AttoTransactionMonitorAsyncBuilder actual constructor(
    private val nodeClient: AttoNodeClientAsync,
    private val accountMonitor: AttoAccountMonitorAsync,
) {
    actual var heightProvider: (AttoAddress) -> AttoHeight = { AttoHeight.MIN }

    actual fun heightProvider(value: (AttoAddress) -> AttoHeight): AttoTransactionMonitorAsyncBuilder =
        apply {
            heightProvider = value
        }

    fun build(dispatcher: CoroutineDispatcher): AttoTransactionMonitorAsync {
        val transactionMonitor = AttoTransactionMonitor(nodeClient.client, accountMonitor.monitor, heightProvider)
        return AttoTransactionMonitorAsync(transactionMonitor, dispatcher)
    }

    fun build(executorService: ExecutorService): AttoTransactionMonitorAsync = build(executorService.asCoroutineDispatcher())

    actual fun build(): AttoTransactionMonitorAsync = build(Dispatchers.Default)
}
