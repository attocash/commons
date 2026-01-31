package cash.atto.commons.node.monitor

import cash.atto.commons.AttoJob
import cash.atto.commons.AttoTransaction
import cash.atto.commons.node.consumeStream
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture
import java.util.function.Function

@JsExportForJs
actual class AttoTransactionMonitorAsync internal actual constructor(
    actual val transactionMonitor: AttoTransactionMonitor,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun onTransaction(
        onTransaction: Function<AttoTransaction, CompletableFuture<Void>>,
        onCancel: Function<Exception?, CompletableFuture<Void>>,
    ): AttoJob =
        scope.consumeStream(
            stream = transactionMonitor.stream(),
            onEach =
                {
                    onTransaction.apply(it.value).await()
                    it.acknowledge()
                },
            onCancel =
                { onCancel.apply(it).await() },
        )

    actual override fun close() {
        scope.cancel()
    }
}

fun AttoTransactionMonitor.toAsync(): AttoTransactionMonitorAsync = AttoTransactionMonitorAsync(this, Dispatchers.Default)
