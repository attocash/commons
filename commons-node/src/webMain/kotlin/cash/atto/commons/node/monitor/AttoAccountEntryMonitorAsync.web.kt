package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoJob
import cash.atto.commons.node.consumeStream
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.js.ExperimentalJsExport

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
actual class AttoAccountEntryMonitorAsync internal actual constructor(
    val accountEntryMonitor: AttoAccountEntryMonitor,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun onAccountEntry(
        onAccountEntry: suspend (AttoAccountEntry) -> Unit,
        onCancel: (Exception?) -> Unit,
    ): AttoJob =
        scope.consumeStream(
            stream = accountEntryMonitor.stream(),
            onEach = {
                onAccountEntry(it.value)
                it.acknowledge()
            },
            onCancel = { onCancel.invoke(it) },
        )

    actual override fun close() {
        scope.cancel()
    }
}

fun AttoAccountEntryMonitor.toAsync(): AttoAccountEntryMonitorAsync = AttoAccountEntryMonitorAsync(this, Dispatchers.Default)
