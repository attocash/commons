package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import kotlinx.coroutines.Job
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

interface JavaSubscription {
    fun cancel()
}

class JavaSubscriptionImpl(
    private val job: Job,
) : JavaSubscription {
    override fun cancel() = job.cancel()
}

// TODO: Merge with JS
interface AttoNodeOperationsJava {
    val network: AttoNetwork

    fun account(addresses: Collection<AttoAddress>): CompletableFuture<Collection<AttoAccount>>

    fun accountEntry(hash: AttoHash): CompletableFuture<AttoAccountEntry>

    fun transaction(hash: AttoHash): CompletableFuture<AttoTransaction>

    fun now(): CompletableFuture<AttoInstant>

    fun publish(transaction: AttoTransaction): CompletableFuture<Unit>

    fun onAccount(
        addresses: Collection<AttoAddress>,
        onUpdate: Consumer<AttoAccount>,
        onDisconnect: Consumer<Throwable>,
    ): JavaSubscription

    fun onReceivable(
        addresses: Collection<AttoAddress>,
        minAmount: AttoAmount,
        onUpdate: Consumer<AttoReceivable>,
        onDisconnect: Consumer<Throwable>,
    ): JavaSubscription

    fun onTransaction(
        heightSearch: HeightSearch,
        onUpdate: Consumer<AttoTransaction>,
        onDisconnect: Consumer<Throwable>,
    ): JavaSubscription

    fun onAccountEntry(
        heightSearch: HeightSearch,
        onUpdate: Consumer<AttoAccountEntry>,
        onDisconnect: Consumer<Throwable>,
    ): JavaSubscription
}
