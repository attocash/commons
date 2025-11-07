package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoJob
import cash.atto.commons.node.AttoConsumer
import cash.atto.commons.node.consumeStream
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.jvm.JvmOverloads

@JsExportForJs
class AttoAccountEntryMonitorAsync(
    private val accountEntryMonitor: AttoAccountEntryMonitor,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    @JvmOverloads
    fun onAccountEntry(
        onAccountEntry: AttoConsumer<AttoAccountEntry>,
        onCancel: AttoConsumer<Exception?>,
    ): AttoJob =
        scope.consumeStream(
            stream = accountEntryMonitor.stream(),
            onEach = {
                onAccountEntry.consume(it.value)
                it.acknowledge()
            },
            onCancel = { onCancel.consume(it) },
        )

    override fun close() {
        scope.cancel()
    }
}
