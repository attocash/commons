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
actual class AttoAccountEntryMonitorAsyncBuilder actual constructor(
    private val nodeClient: AttoNodeClientAsync,
    private val accountMonitor: AttoAccountMonitorAsync,
) {
    private var heightProvider: suspend (AttoAddress) -> AttoHeight = { AttoHeight.MIN }

    fun heightProvider(value: (AttoAddress) -> CompletableFuture<AttoHeight>): AttoAccountEntryMonitorAsyncBuilder =
        apply {
            heightProvider = { value.invoke(it).await() }
        }

    fun build(dispatcher: CoroutineDispatcher): AttoAccountEntryMonitorAsync {
        val accountEntryMonitor = AttoAccountEntryMonitor(nodeClient.client, accountMonitor.accountMonitor, heightProvider)
        return AttoAccountEntryMonitorAsync(accountEntryMonitor, dispatcher)
    }

    fun build(executorService: ExecutorService): AttoAccountEntryMonitorAsync = build(executorService.asCoroutineDispatcher())

    actual fun build(): AttoAccountEntryMonitorAsync = build(Dispatchers.Default)
}
