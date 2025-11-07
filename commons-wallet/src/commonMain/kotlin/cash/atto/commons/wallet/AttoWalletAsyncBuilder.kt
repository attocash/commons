package cash.atto.commons.wallet

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoFuture
import cash.atto.commons.AttoKeyIndex
import cash.atto.commons.AttoSeed
import cash.atto.commons.AttoSigner
import cash.atto.commons.node.AttoNodeClientAsync
import cash.atto.commons.node.monitor.AttoAccountMonitorAsync
import cash.atto.commons.toAttoAmount
import cash.atto.commons.utils.JsExportForJs
import cash.atto.commons.worker.AttoWorkerAsync
import kotlin.js.JsName

@JsExportForJs
expect class AttoWalletAsyncBuilder(
    clientAsync: AttoNodeClientAsync,
    workerAsync: AttoWorkerAsync,
) {
    @JsName("signerProviderFunction")
    fun signerProvider(value: AttoSignerProvider): AttoWalletAsyncBuilder

    @JsName("signerProviderSeed")
    fun signerProvider(value: AttoSeed): AttoWalletAsyncBuilder

    fun enableAutoReceiver(
        monitor: AttoAccountMonitorAsync,
        minAmount: AttoAmount = 1UL.toAttoAmount(),
        retryAfterSeconds: Int,
        defaultRepresentativeAddressProvider: (() -> AttoAddress),
    ): AttoWalletAsyncBuilder

    fun build(): AttoWalletAsync
}

@JsExportForJs
interface AttoSignerProvider {
    fun get(index: AttoKeyIndex): AttoFuture<AttoSigner>
}
