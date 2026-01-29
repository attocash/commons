package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoFuture
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoJob
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.submit
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmSynthetic

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
class AttoNodeClientAsync(
    val client: AttoNodeClient,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    @JsExport.Ignore
    fun account(publicKey: AttoPublicKey): AttoFuture<AttoAccount?> = scope.submit { client.account(publicKey) }

    @JsExport.Ignore
    fun account(addresses: Collection<AttoAddress>): AttoFuture<Collection<AttoAccount>> = scope.submit { client.account(addresses) }

    @JvmSynthetic
    fun account(addresses: Array<AttoAddress>): AttoFuture<Array<AttoAccount>> =
        scope.submit { client.account(addresses.toList()).toTypedArray() }

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

    @JsExport.Ignore
    fun onAccount(
        publicKey: AttoPublicKey,
        onAccount: (AttoAccount) -> Unit,
        onCancel: AttoConsumer<Exception?>,
    ): AttoJob =
        scope.consumeStream(
            stream = client.accountStream(publicKey),
            onEach = {
                onAccount(it)
            },
            onCancel = { onCancel.consume(it) },
        )

    fun onAccount(
        addresses: Collection<AttoAddress>,
        onAccount: (AttoAccount) -> Unit,
        onCancel: AttoConsumer<Exception?>,
    ): AttoJob =
        scope.consumeStream(
            stream = client.accountStream(addresses),
            onEach = {
                onAccount(it)
            },
            onCancel = { onCancel.consume(it) },
        )

    @JsExport.Ignore
    @JvmOverloads
    fun onReceivable(
        publicKey: AttoPublicKey,
        minAmount: AttoAmount = AttoAmount(1U),
        onReceivable: AttoConsumer<AttoReceivable>,
        onCancel: AttoConsumer<Exception?>,
    ): AttoJob =
        scope.consumeStream(
            stream = client.receivableStream(publicKey, minAmount),
            onEach = {
                onReceivable.consume(it)
            },
            onCancel = { onCancel.consume(it) },
        )

    @JvmOverloads
    fun onReceivable(
        addresses: Collection<AttoAddress>,
        minAmount: AttoAmount = AttoAmount(1U),
        onReceivable: AttoConsumer<AttoReceivable>,
        onCancel: AttoConsumer<Exception?>,
    ): AttoJob =
        scope.consumeStream(
            stream = client.receivableStream(addresses, minAmount),
            onEach = {
                onReceivable.consume(it)
            },
            onCancel = { onCancel.consume(it) },
        )

    fun accountEntry(hash: AttoHash): AttoFuture<AttoAccountEntry> = scope.submit { client.accountEntry(hash) }

    @JsExport.Ignore
    fun onAccountEntry(
        publicKey: AttoPublicKey,
        fromHeight: AttoHeight = AttoHeight(1UL),
        toHeight: AttoHeight? = null,
        onAccountEntry: AttoConsumer<AttoAccountEntry>,
        onCancel: AttoConsumer<Exception?>,
    ): AttoJob =
        scope.consumeStream(
            stream = client.accountEntryStream(publicKey, fromHeight, toHeight),
            onEach = {
                onAccountEntry.consume(it)
            },
            onCancel = { onCancel.consume(it) },
        )

    fun onAccountEntry(
        heightSearch: HeightSearch,
        onAccountEntry: AttoConsumer<AttoAccountEntry>,
        onCancel: AttoConsumer<Exception?>,
    ): AttoJob =
        scope.consumeStream(
            stream = client.accountEntryStream(heightSearch),
            onEach = {
                onAccountEntry.consume(it)
            },
            onCancel = { onCancel.consume(it) },
        )

    fun transaction(hash: AttoHash): AttoFuture<AttoTransaction> = scope.submit { client.transaction(hash) }

    @JsExport.Ignore
    fun onTransaction(
        publicKey: AttoPublicKey,
        fromHeight: AttoHeight = AttoHeight(1UL),
        toHeight: AttoHeight? = null,
        onTransaction: AttoConsumer<AttoTransaction>,
        onCancel: AttoConsumer<Exception?>,
    ): AttoJob =
        scope.consumeStream(
            stream = client.transactionStream(publicKey, fromHeight, toHeight),
            onEach = {
                onTransaction.consume(it)
            },
            onCancel = { onCancel.consume(it) },
        )

    fun onTransaction(
        heightSearch: HeightSearch,
        onTransaction: AttoConsumer<AttoTransaction>,
        onCancel: AttoConsumer<Exception?>,
    ): AttoJob =
        scope.consumeStream(
            stream = client.transactionStream(heightSearch),
            onEach = {
                onTransaction.consume(it)
            },
            onCancel = { onCancel.consume(it) },
        )

    fun now(): AttoFuture<AttoInstant> = scope.submit { client.now() }

    fun publish(transaction: AttoTransaction): AttoFuture<Unit> = scope.submit { client.publish(transaction) }

    override fun close() {
        scope.cancel()
    }
}

fun AttoNodeClient.toAsync(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoNodeClientAsync =
    AttoNodeClientAsync(this, dispatcher)
