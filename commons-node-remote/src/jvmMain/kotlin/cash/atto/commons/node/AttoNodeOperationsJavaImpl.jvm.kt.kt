package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.function.Consumer

private class AttoNodeOperationsJavaImpl(
    private val delegate: AttoNodeOperations,
    executorService: ExecutorService,
) : AttoNodeOperationsJava {
    private val scope = CoroutineScope(executorService.asCoroutineDispatcher() + SupervisorJob())

    override val network: AttoNetwork get() = delegate.network

    override fun account(addresses: Collection<AttoAddress>): CompletableFuture<Collection<AttoAccount>> =
        scope.future { delegate.account(addresses) }

    override fun accountEntry(hash: AttoHash): CompletableFuture<AttoAccountEntry> = scope.future { delegate.accountEntry(hash) }

    override fun transaction(hash: AttoHash): CompletableFuture<AttoTransaction> = scope.future { delegate.transaction(hash) }

    override fun now(): CompletableFuture<Instant> = scope.future { delegate.now() }

    override fun publish(transaction: AttoTransaction): CompletableFuture<Unit> =
        scope.future<Unit> {
            delegate.publish(transaction)
        }

    override fun onAccount(
        addresses: Collection<AttoAddress>,
        onUpdate: Consumer<AttoAccount>,
        onDisconnect: Consumer<Throwable>,
    ): JavaSubscription {
        val job =
            scope.launch {
                try {
                    delegate.accountStream(addresses).collect { onUpdate.accept(it) }
                } catch (t: Throwable) {
                    onDisconnect.accept(t)
                }
            }
        return JavaSubscriptionImpl(job)
    }

    override fun onReceivable(
        addresses: Collection<AttoAddress>,
        minAmount: AttoAmount,
        onUpdate: Consumer<AttoReceivable>,
        onDisconnect: Consumer<Throwable>,
    ): JavaSubscription {
        val job =
            scope.launch {
                try {
                    delegate.receivableStream(addresses, minAmount).collect { onUpdate.accept(it) }
                } catch (t: Throwable) {
                    onDisconnect.accept(t)
                }
            }
        return JavaSubscriptionImpl(job)
    }

    override fun onTransaction(
        heightSearch: HeightSearch,
        onUpdate: Consumer<AttoTransaction>,
        onDisconnect: Consumer<Throwable>,
    ): JavaSubscription {
        val job =
            scope.launch {
                try {
                    delegate.transactionStream(heightSearch).collect { onUpdate.accept(it) }
                } catch (t: Throwable) {
                    onDisconnect.accept(t)
                }
            }
        return JavaSubscriptionImpl(job)
    }

    override fun onAccountEntry(
        heightSearch: HeightSearch,
        onUpdate: Consumer<AttoAccountEntry>,
        onDisconnect: Consumer<Throwable>,
    ): JavaSubscription {
        val job =
            scope.launch {
                try {
                    delegate.accountEntryStream(heightSearch).collect { onUpdate.accept(it) }
                } catch (t: Throwable) {
                    onDisconnect.accept(t)
                }
            }
        return JavaSubscriptionImpl(job)
    }
}

fun createCustomAttoNodeJavaClient(
    network: AttoNetwork,
    baseUrl: String,
    executorService: ExecutorService,
): AttoNodeOperationsJava {
    val operations = AttoNodeOperations.custom(network, baseUrl)
    return AttoNodeOperationsJavaImpl(operations, executorService)
}
