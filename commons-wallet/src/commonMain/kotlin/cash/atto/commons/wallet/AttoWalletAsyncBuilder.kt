package cash.atto.commons.wallet

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoKeyIndex
import cash.atto.commons.AttoSeed
import cash.atto.commons.AttoSigner
import cash.atto.commons.node.AttoFuture
import cash.atto.commons.node.AttoNodeClientAsync
import cash.atto.commons.node.monitor.AttoAccountMonitorAsync
import cash.atto.commons.toAttoAmount
import cash.atto.commons.worker.AttoWorkerAsync
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

expect class AttoWalletAsyncBuilder(
    clientAsync: AttoNodeClientAsync,
    workerAsync: AttoWorkerAsync,
) {
    fun signerProvider(value: (AttoKeyIndex) -> AttoFuture<AttoSigner>): AttoWalletAsyncBuilder

    fun signerProvider(value: AttoSeed): AttoWalletAsyncBuilder

    fun enableAutoReceiver(
        monitor: AttoAccountMonitorAsync,
        minAmount: AttoAmount = 1UL.toAttoAmount(),
        duration: Duration = 10.seconds,
        defaultRepresentativeAddressProvider: (() -> AttoAddress),
    ): AttoWalletAsyncBuilder

    fun build(): AttoWalletAsync
}
