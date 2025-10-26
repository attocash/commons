package cash.atto.commons.node.monitor

import cash.atto.commons.node.AttoNodeClientAsync
import kotlinx.coroutines.Dispatchers

actual class AttoAccountMonitorAsyncBuilder actual constructor(
    private val nodeClient: AttoNodeClientAsync,
) {
    actual fun build(): AttoAccountMonitorAsync {
        val monitor = nodeClient.client.createAccountMonitor()
        return AttoAccountMonitorAsync(monitor, Dispatchers.Default)
    }
}
