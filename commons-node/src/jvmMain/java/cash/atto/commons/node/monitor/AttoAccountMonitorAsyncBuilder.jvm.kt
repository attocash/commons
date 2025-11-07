package cash.atto.commons.node.monitor

import cash.atto.commons.node.AttoNodeClientAsync
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService

@JsExportForJs
actual class AttoAccountMonitorAsyncBuilder actual constructor(
    private val nodeClient: AttoNodeClientAsync,
) {
    fun build(dispatcher: CoroutineDispatcher): AttoAccountMonitorAsync {
        val monitor = nodeClient.client.createAccountMonitor()
        return AttoAccountMonitorAsync(monitor, dispatcher)
    }

    fun build(executorService: ExecutorService): AttoAccountMonitorAsync = build(executorService.asCoroutineDispatcher())

    actual fun build(): AttoAccountMonitorAsync = build(Dispatchers.Default)
}
