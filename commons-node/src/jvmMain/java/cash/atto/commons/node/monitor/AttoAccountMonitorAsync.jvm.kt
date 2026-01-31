package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoJob
import cash.atto.commons.AttoReceivable
import cash.atto.commons.node.consumeStream
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

@JsExportForJs
actual class AttoAccountMonitorAsync internal actual constructor(
    actual val monitor: AttoAccountMonitor,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun monitor(addresses: Collection<AttoAddress>): CompletableFuture<Unit> = scope.future { monitor.monitor(addresses) }

    fun monitor(address: AttoAddress): CompletableFuture<Unit> = scope.future { monitor.monitor(address) }

    fun getAccounts(): CompletableFuture<Collection<AttoAccount>> = scope.future { monitor.getAccounts() }

    @JvmOverloads
    fun onReceivable(
        minAmount: AttoAmount = AttoAmount.MIN,
        onReceivable: (AttoReceivable) -> Any,
        onCancel: (Exception?) -> Any,
    ): AttoJob =
        scope.consumeStream(
            stream = monitor.receivableStream(minAmount),
            onEach = { onReceivable.invoke(it) },
            onCancel = { onCancel.invoke(it) },
        )

    actual override fun close() {
        scope.cancel()
    }
}

fun AttoAccountMonitor.toAsync(): AttoAccountMonitorAsync = AttoAccountMonitorAsync(this, Dispatchers.Default)
