package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoJob
import cash.atto.commons.node.consumeStream
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture
import java.util.function.Function

@JsExportForJs
actual class AttoAccountEntryMonitorAsync actual constructor(
    val accountEntryMonitor: AttoAccountEntryMonitor,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun onAccountEntry(
        onAccountEntry: Function<AttoAccountEntry, CompletableFuture<Void>>,
        onCancel: Function<Exception?, CompletableFuture<Void>>,
    ): AttoJob =
        scope.consumeStream(
            stream = accountEntryMonitor.stream(),
            onEach = {
                onAccountEntry.apply(it.value).await()
                it.acknowledge()
            },
            onCancel = { onCancel.apply(it).await() },
        )

    actual override fun close() {
    }
}
