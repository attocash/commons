package cash.atto.commons.wallet

import cash.atto.commons.AttoKeyIndex
import cash.atto.commons.AttoSeed
import cash.atto.commons.AttoSigner
import cash.atto.commons.node.AttoFuture
import cash.atto.commons.node.AttoNodeClientAsync
import cash.atto.commons.node.await
import cash.atto.commons.toSigner
import cash.atto.commons.worker.AttoWorkerAsync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService

actual class AttoWalletAsyncBuilder actual constructor(
    private val clientAsync: AttoNodeClientAsync,
    private val workerAsync: AttoWorkerAsync,
) {
    private var signerProvider: (suspend (AttoKeyIndex) -> AttoSigner)? = null

    actual fun signerProvider(value: (AttoKeyIndex) -> AttoFuture<AttoSigner>): AttoWalletAsyncBuilder =
        apply {
            signerProvider = { index -> value(index).await() }
        }

    actual fun signerProvider(value: AttoSeed): AttoWalletAsyncBuilder =
        apply {
            signerProvider = value::toSigner
        }

    fun build(dispatcher: CoroutineDispatcher): AttoWalletAsync {
        val wallet = AttoWallet(clientAsync.client, workerAsync.worker, signerProvider!!)
        return AttoWalletAsync(wallet, dispatcher)
    }

    fun build(executorService: ExecutorService): AttoWalletAsync = build(executorService.asCoroutineDispatcher())

    actual fun build(): AttoWalletAsync = build(Dispatchers.Default)
}
