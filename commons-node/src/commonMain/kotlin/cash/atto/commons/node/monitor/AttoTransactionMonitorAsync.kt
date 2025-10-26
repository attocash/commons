package cash.atto.commons.node.monitor

import cash.atto.commons.AttoTransaction
import cash.atto.commons.node.AttoConsumer
import cash.atto.commons.node.AttoJob
import cash.atto.commons.node.consumeStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.jvm.JvmOverloads

class AttoTransactionMonitorAsync(
    private val transactionMonitor: AttoTransactionMonitor,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    @JvmOverloads
    fun onTransaction(
        onTransaction: AttoConsumer<AttoTransaction>,
        onCancel: AttoConsumer<Exception?>,
    ): AttoJob =
        scope.consumeStream(
            stream = transactionMonitor.stream(),
            onEach = {
                onTransaction.consume(it.value)
                it.acknowledge()
            },
            onCancel = { onCancel.consume(it) },
        )

    override fun close() {
        scope.cancel()
    }
}
