package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoJob
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.cancellation.CancellationException

@JsExportForJs
actual class AttoNodeClientAsync actual constructor(
    actual val client: AttoNodeClient,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun account(publicKey: AttoPublicKey): CompletableFuture<AttoAccount?> = scope.future { client.account(publicKey) }

    fun account(addresses: Collection<AttoAddress>): CompletableFuture<Collection<AttoAccount>> = scope.future { client.account(addresses) }

    private inline fun <T> CoroutineScope.consumeStream(
        stream: Flow<T>,
        crossinline onEach: suspend (T) -> Unit,
        noinline onCancel: (Exception?) -> Unit,
    ): AttoJob =
        AttoJob(
            launch {
                try {
                    stream.collect { onEach(it) }
                    onCancel(null)
                } catch (e: CancellationException) {
                    onCancel(null)
                    throw e
                } catch (e: Exception) {
                    onCancel(e)
                }
            },
        )

    fun onAccount(
        publicKey: AttoPublicKey,
        onAccount: (AttoAccount) -> Unit,
        onCancel: (Exception?) -> Unit,
    ): AttoJob =
        scope.consumeStream(
            stream = client.accountStream(publicKey),
            onEach = { onAccount(it) },
            onCancel = { onCancel.invoke(it) },
        )

    fun onAccount(
        addresses: Collection<AttoAddress>,
        onAccount: (AttoAccount) -> Unit,
        onCancel: (Exception?) -> Unit,
    ): AttoJob =
        scope.consumeStream(
            stream = client.accountStream(addresses),
            onEach = { onAccount(it) },
            onCancel = { onCancel.invoke(it) },
        )

    @JvmOverloads
    fun onReceivable(
        publicKey: AttoPublicKey,
        minAmount: AttoAmount = AttoAmount(1U),
        onReceivable: (AttoReceivable) -> Unit,
        onCancel: (Exception?) -> Unit,
    ): AttoJob =
        scope.consumeStream(
            stream = client.receivableStream(publicKey, minAmount),
            onEach = { onReceivable.invoke(it) },
            onCancel = { onCancel.invoke(it) },
        )

    @JvmOverloads
    fun onReceivable(
        addresses: Collection<AttoAddress>,
        minAmount: AttoAmount = AttoAmount(1U),
        onReceivable: (AttoReceivable) -> Unit,
        onCancel: (Exception?) -> Unit,
    ): AttoJob =
        scope.consumeStream(
            stream = client.receivableStream(addresses, minAmount),
            onEach = { onReceivable.invoke(it) },
            onCancel = { onCancel.invoke(it) },
        )

    fun accountEntry(hash: AttoHash): CompletableFuture<AttoAccountEntry> = scope.future { client.accountEntry(hash) }

    @JvmOverloads
    fun onAccountEntry(
        publicKey: AttoPublicKey,
        fromHeight: AttoHeight = AttoHeight(1UL),
        toHeight: AttoHeight? = null,
        onAccountEntry: (AttoAccountEntry) -> Unit,
        onCancel: (Exception?) -> Unit,
    ): AttoJob =
        scope.consumeStream(
            stream = client.accountEntryStream(publicKey, fromHeight, toHeight),
            onEach = { onAccountEntry.invoke(it) },
            onCancel = { onCancel.invoke(it) },
        )

    fun onAccountEntry(
        heightSearch: HeightSearch,
        onAccountEntry: (AttoAccountEntry) -> Unit,
        onCancel: (Exception?) -> Unit,
    ): AttoJob =
        scope.consumeStream(
            stream = client.accountEntryStream(heightSearch),
            onEach = { onAccountEntry.invoke(it) },
            onCancel = { onCancel.invoke(it) },
        )

    fun transaction(hash: AttoHash): CompletableFuture<AttoTransaction> = scope.future { client.transaction(hash) }

    @JvmOverloads
    fun onTransaction(
        publicKey: AttoPublicKey,
        fromHeight: AttoHeight = AttoHeight(1UL),
        toHeight: AttoHeight? = null,
        onTransaction: (AttoTransaction) -> Unit,
        onCancel: (Exception?) -> Unit,
    ): AttoJob =
        scope.consumeStream(
            stream = client.transactionStream(publicKey, fromHeight, toHeight),
            onEach = { onTransaction.invoke(it) },
            onCancel = { onCancel.invoke(it) },
        )

    fun onTransaction(
        heightSearch: HeightSearch,
        onTransaction: (AttoTransaction) -> Unit,
        onCancel: (Exception?) -> Unit,
    ): AttoJob =
        scope.consumeStream(
            stream = client.transactionStream(heightSearch),
            onEach = { onTransaction.invoke(it) },
            onCancel = { onCancel.invoke(it) },
        )

    fun now(): CompletableFuture<AttoInstant> = scope.future { client.now() }

    fun publish(transaction: AttoTransaction): CompletableFuture<Unit> = scope.future { client.publish(transaction) }

    actual override fun close() {
        scope.cancel()
    }
}

fun AttoNodeClient.toAsync(): AttoNodeClientAsync = AttoNodeClientAsync(this, Dispatchers.Default)
