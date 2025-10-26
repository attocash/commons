package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoReceivable
import cash.atto.commons.node.AttoConsumer
import cash.atto.commons.node.AttoFuture
import cash.atto.commons.node.AttoJob
import cash.atto.commons.node.consumeStream
import cash.atto.commons.node.submit
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
class AttoAccountMonitorAsync internal constructor(
    internal val monitor: AttoAccountMonitor,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    @JsName("monitorMap")
    fun monitor(addresses: Collection<AttoAddress>): AttoFuture<Unit> = scope.submit { monitor.monitor(addresses) }

    @JsName("monitor")
    fun monitor(address: AttoAddress): AttoFuture<Unit> = scope.submit { monitor.monitor(address) }

    fun getAccounts(): AttoFuture<Collection<AttoAccount>> = scope.submit { monitor.getAccounts() }

    @JvmOverloads
    fun onReceivable(
        minAmount: AttoAmount = AttoAmount.MIN,
        onReceivable: AttoConsumer<AttoReceivable>,
        onCancel: AttoConsumer<Exception?>,
    ): AttoJob =
        scope.consumeStream(
            stream = monitor.receivableStream(minAmount),
            onEach = { onReceivable.consume(it) },
            onCancel = { onCancel.consume(it) },
        )

    override fun close() {
        scope.cancel()
    }
}

fun AttoAccountMonitor.toAsync(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoAccountMonitorAsync =
    AttoAccountMonitorAsync(this, dispatcher)
