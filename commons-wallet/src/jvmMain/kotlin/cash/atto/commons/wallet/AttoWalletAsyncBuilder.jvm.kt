package cash.atto.commons.wallet

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoKeyIndex
import cash.atto.commons.AttoSeed
import cash.atto.commons.AttoSigner
import cash.atto.commons.node.AttoNodeClientAsync
import cash.atto.commons.node.monitor.AttoAccountMonitorAsync
import cash.atto.commons.toAttoAmount
import cash.atto.commons.utils.JsExportForJs
import cash.atto.commons.worker.AttoWorkerAsync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import kotlin.time.Duration.Companion.seconds

@JsExportForJs
actual class AttoWalletAsyncBuilder actual constructor(
    private val clientAsync: AttoNodeClientAsync,
    private val workerAsync: AttoWorkerAsync,
) {
    private val configuration = AttoWalletAsyncConfiguration()

    actual fun signerProvider(value: AttoSignerProvider): AttoWalletAsyncBuilder =
        apply {
            configuration.signerProvider = { index -> value.get(index).await() }
        }

    actual fun signerProvider(value: AttoSeed): AttoWalletAsyncBuilder =
        apply {
            configuration.signerProvider = value::toSigner
        }

    @JvmSynthetic
    actual fun enableAutoReceiver(
        monitor: AttoAccountMonitorAsync,
        minAmount: AttoAmount,
        retryAfterSeconds: Int,
        defaultRepresentativeAddressProvider: () -> AttoAddress,
    ): AttoWalletAsyncBuilder =
        apply {
            configuration.enableAutoReceiver(
                monitor.accountMonitor,
                minAmount,
                retryAfterSeconds.seconds,
                defaultRepresentativeAddressProvider,
            )
        }

    @JvmOverloads
    fun enableAutoReceiver(
        monitor: AttoAccountMonitorAsync,
        minAmount: AttoAmount = 1UL.toAttoAmount(),
        duration: java.time.Duration = java.time.Duration.ofSeconds(10),
        defaultRepresentativeAddressProvider: (() -> AttoAddress),
    ): AttoWalletAsyncBuilder =
        apply {
            enableAutoReceiver(monitor, minAmount, duration.toSeconds().toInt(), defaultRepresentativeAddressProvider)
        }

    fun build(dispatcher: CoroutineDispatcher): AttoWalletAsync = configuration.build(clientAsync.client, workerAsync.worker, dispatcher)

    fun build(executorService: ExecutorService): AttoWalletAsync = build(executorService.asCoroutineDispatcher())

    actual fun build(): AttoWalletAsync = build(Dispatchers.Default)
}

@JsExportForJs
actual interface AttoSignerProvider {
    suspend fun get(index: AttoKeyIndex): CompletableFuture<AttoSigner>
}
