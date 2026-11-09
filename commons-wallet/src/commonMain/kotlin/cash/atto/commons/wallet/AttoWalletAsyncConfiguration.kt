package cash.atto.commons.wallet

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoKeyIndex
import cash.atto.commons.AttoSigner
import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.node.monitor.AttoAccountMonitor
import cash.atto.commons.worker.AttoWorker
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.time.Duration

internal class AttoWalletAsyncConfiguration {
    private class AutoReceiver(
        val monitor: AttoAccountMonitor,
        val minAmount: AttoAmount,
        val retryAfter: Duration,
        val representativeAddress: () -> AttoAddress,
    )

    var signerProvider: (suspend (AttoKeyIndex) -> AttoSigner)? = null
    private var autoReceiver: AutoReceiver? = null

    fun enableAutoReceiver(
        monitor: AttoAccountMonitor,
        minAmount: AttoAmount,
        retryAfter: Duration,
        representativeAddress: () -> AttoAddress,
    ) {
        autoReceiver = AutoReceiver(monitor, minAmount, retryAfter, representativeAddress)
    }

    fun build(
        client: AttoNodeClient,
        worker: AttoWorker,
        dispatcher: CoroutineDispatcher,
    ): AttoWalletAsync {
        val wallet = AttoWallet(client, worker, signerProvider!!)
        val walletAsync = AttoWalletAsync(wallet, dispatcher)
        autoReceiver?.let {
            wallet.startAutoReceiver(walletAsync.scope, it.monitor, it.minAmount, it.retryAfter, it.representativeAddress)
        }
        return walletAsync
    }
}
