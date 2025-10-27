package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.node.AttoNodeClientAsync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService

actual class AttoAccountEntryMonitorAsyncBuilder actual constructor(
    private val nodeClient: AttoNodeClientAsync,
    private val accountMonitor: AttoAccountMonitorAsync,
) {
    private var heightProvider: (AttoAddress) -> AttoHeight = { AttoHeight.MIN }

    actual fun heightProvider(value: (AttoAddress) -> AttoHeight): AttoAccountEntryMonitorAsyncBuilder =
        apply {
            heightProvider = value
        }

    fun build(dispatcher: CoroutineDispatcher): AttoAccountEntryMonitorAsync {
        val accountEntryMonitor = AttoAccountEntryMonitor(nodeClient.client, accountMonitor.monitor, heightProvider)
        return AttoAccountEntryMonitorAsync(accountEntryMonitor, dispatcher)
    }

    fun build(executorService: ExecutorService): AttoAccountEntryMonitorAsync = build(executorService.asCoroutineDispatcher())

    actual fun build(): AttoAccountEntryMonitorAsync = build(Dispatchers.Default)
}
