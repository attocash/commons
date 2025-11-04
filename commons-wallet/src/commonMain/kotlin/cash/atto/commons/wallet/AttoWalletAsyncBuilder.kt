package cash.atto.commons.wallet

import cash.atto.commons.AttoKeyIndex
import cash.atto.commons.AttoSeed
import cash.atto.commons.AttoSigner
import cash.atto.commons.node.AttoFuture
import cash.atto.commons.node.AttoNodeClientAsync
import cash.atto.commons.worker.AttoWorkerAsync

expect class AttoWalletAsyncBuilder(
    clientAsync: AttoNodeClientAsync,
    workerAsync: AttoWorkerAsync,
) {
    fun signerProvider(value: (AttoKeyIndex) -> AttoFuture<AttoSigner>): AttoWalletAsyncBuilder

    fun signerProvider(value: AttoSeed): AttoWalletAsyncBuilder

    fun build(): AttoWalletAsync
}
