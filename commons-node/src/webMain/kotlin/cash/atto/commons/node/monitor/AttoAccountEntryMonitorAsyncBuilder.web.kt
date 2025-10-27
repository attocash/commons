package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.node.AttoNodeClientAsync
import kotlinx.coroutines.Dispatchers

actual class AttoAccountEntryMonitorAsyncBuilder actual constructor(
    private val nodeClient: AttoNodeClientAsync,
    private val accountMonitor: AttoAccountMonitorAsync,
) {
    private var heightProvider: (AttoAddress) -> AttoHeight = { AttoHeight.MIN }

    actual fun heightProvider(value: (AttoAddress) -> AttoHeight): AttoAccountEntryMonitorAsyncBuilder =
        apply {
            heightProvider = value
        }

    actual fun build(): AttoAccountEntryMonitorAsync {
        val accountEntryMonitor = AttoAccountEntryMonitor(nodeClient.client, accountMonitor.monitor, heightProvider)
        return AttoAccountEntryMonitorAsync(accountEntryMonitor, Dispatchers.Default)
    }
}
