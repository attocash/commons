package cash.atto.commons.wallet

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoKeyIndex
import cash.atto.commons.AttoSeed
import cash.atto.commons.AttoSigner
import cash.atto.commons.node.AttoNodeClientAsync
import cash.atto.commons.node.monitor.AttoAccountMonitorAsync
import cash.atto.commons.utils.JsExportForJs
import cash.atto.commons.worker.AttoWorkerAsync
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.seconds

@JsExportForJs
actual class AttoWalletAsyncBuilder actual constructor(
    private val clientAsync: AttoNodeClientAsync,
    private val workerAsync: AttoWorkerAsync,
) {
    private val configuration = AttoWalletAsyncConfiguration()

    actual fun signerProvider(value: AttoSignerProvider): AttoWalletAsyncBuilder =
        apply {
            configuration.signerProvider = { index -> value.get(index) }
        }

    actual fun signerProvider(value: AttoSeed): AttoWalletAsyncBuilder =
        apply {
            configuration.signerProvider = value::toSigner
        }

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

    actual fun build(): AttoWalletAsync = configuration.build(clientAsync.client, workerAsync.worker, Dispatchers.Default)
}

actual interface AttoSignerProvider {
    suspend fun get(index: AttoKeyIndex): AttoSigner
}
