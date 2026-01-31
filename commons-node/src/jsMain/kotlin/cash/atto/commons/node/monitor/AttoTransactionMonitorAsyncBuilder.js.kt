package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.node.AttoNodeClientAsync
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlin.js.Promise

@JsExportForJs
actual class AttoTransactionMonitorAsyncBuilder actual constructor(
    private val nodeClient: AttoNodeClientAsync,
    private val accountMonitor: AttoAccountMonitorAsync,
) {
    private var heightProvider: suspend (AttoAddress) -> AttoHeight = { AttoHeight.MIN }

    fun heightProvider(value: (AttoAddress) -> Promise<AttoHeight>): AttoTransactionMonitorAsyncBuilder =
        apply {
            heightProvider = { address -> value.invoke(address).await() }
        }

    actual fun build(): AttoTransactionMonitorAsync {
        val transactionMonitor = AttoTransactionMonitor(nodeClient.client, accountMonitor.monitor, heightProvider)
        return AttoTransactionMonitorAsync(transactionMonitor, Dispatchers.Default)
    }
}
