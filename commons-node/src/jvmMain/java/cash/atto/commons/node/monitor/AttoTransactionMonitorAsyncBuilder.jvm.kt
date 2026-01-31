package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.node.AttoNodeClientAsync
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

@JsExportForJs
actual class AttoTransactionMonitorAsyncBuilder actual constructor(
    private val nodeClient: AttoNodeClientAsync,
    private val accountMonitor: AttoAccountMonitorAsync,
) {
    private var heightProvider: suspend (AttoAddress) -> AttoHeight = { AttoHeight.MIN }

    fun heightProvider(value: (AttoAddress) -> CompletableFuture<AttoHeight>): AttoTransactionMonitorAsyncBuilder =
        apply {
            heightProvider = { value.invoke(it).await() }
        }

    fun build(dispatcher: CoroutineDispatcher): AttoTransactionMonitorAsync {
        val transactionMonitor = AttoTransactionMonitor(nodeClient.client, accountMonitor.monitor, heightProvider)
        return AttoTransactionMonitorAsync(transactionMonitor, dispatcher)
    }

    fun build(executorService: ExecutorService): AttoTransactionMonitorAsync = build(executorService.asCoroutineDispatcher())

    actual fun build(): AttoTransactionMonitorAsync = build(Dispatchers.Default)
}
