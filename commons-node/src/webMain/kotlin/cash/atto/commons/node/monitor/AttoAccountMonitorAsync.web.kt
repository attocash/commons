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
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
actual class AttoAccountMonitorAsync internal actual constructor(
    actual val accountMonitor: AttoAccountMonitor,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    @JsName("monitorAddressArray")
    suspend fun monitor(addresses: Array<AttoAddress>): Unit = accountMonitor.monitor(addresses.toList())

    @JsName("monitorAddress")
    suspend fun monitor(address: AttoAddress): Unit = accountMonitor.monitor(address)

    suspend fun getAccounts(): Array<AttoAccount> = accountMonitor.getAccounts().toTypedArray()

    fun onReceivable(
        minAmount: AttoAmount = AttoAmount.MIN,
        onReceivable: (AttoReceivable) -> Any,
        onCancel: (Exception?) -> Any,
    ): AttoJob =
        scope.consumeStream(
            stream = accountMonitor.receivableStream(minAmount),
            onEach = { onReceivable.invoke(it) },
            onCancel = { onCancel.invoke(it) },
        )

    actual override fun close() {
        scope.cancel()
    }
}

fun AttoAccountMonitor.toAsync(): AttoAccountMonitorAsync = AttoAccountMonitorAsync(this, Dispatchers.Default)
