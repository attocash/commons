package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.node.AttoNodeClientAsync
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlin.js.Promise

@JsExportForJs
actual class AttoAccountEntryMonitorAsyncBuilder actual constructor(
    private val nodeClient: AttoNodeClientAsync,
    private val accountMonitor: AttoAccountMonitorAsync,
) {
    private var heightProvider: suspend (AttoAddress) -> AttoHeight = { AttoHeight.MIN }

    fun heightProvider(value: (AttoAddress) -> Promise<AttoHeight>): AttoAccountEntryMonitorAsyncBuilder =
        apply {
            heightProvider = { address -> value.invoke(address).await() }
        }

    actual fun build(): AttoAccountEntryMonitorAsync {
        val accountEntryMonitor = AttoAccountEntryMonitor(nodeClient.client, accountMonitor.monitor, heightProvider)
        return AttoAccountEntryMonitorAsync(accountEntryMonitor, Dispatchers.Default)
    }
}
