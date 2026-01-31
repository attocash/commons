package cash.atto.commons.wallet

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoJob
import cash.atto.commons.AttoKeyIndex
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoSeed
import cash.atto.commons.AttoSigner
import cash.atto.commons.AttoTransaction
import cash.atto.commons.compareTo
import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.node.monitor.AttoAccountMonitor
import cash.atto.commons.toAttoAmount
import cash.atto.commons.toAttoIndex
import cash.atto.commons.toSigner
import cash.atto.commons.utils.JsExportForJs
import cash.atto.commons.worker.AttoWorker
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class AttoWallet(
    private val client: AttoNodeClient,
    private val worker: AttoWorker,
    private val signerProvider: suspend (AttoKeyIndex) -> AttoSigner,
) {
    companion object {}

    private val mutex = Mutex()
    private val accounts = WalletAccounts()

    suspend fun openAccount(indexes: Collection<AttoKeyIndex>): Collection<AttoWalletAccount> {
        if (indexes.isEmpty()) return emptyList()
        mutex.withLock {
            val indexMap = mutableMapOf<AttoAddress, AttoKeyIndex>()

            indexes.forEach {
                val signer = signerProvider.invoke(it)
                indexMap[signer.address] = it
            }

            val attoAccountMap =
                client
                    .account(indexMap.keys.toList())
                    .asSequence()
                    .map { it.address to it }
                    .toMap()

            val newAccounts =
                indexMap.map {
                    AttoWalletAccount(it.value, it.key, attoAccountMap[it.key])
                }

            accounts.add(newAccounts)

            return newAccounts
        }
    }

    suspend fun openAccount(
        fromIndex: AttoKeyIndex,
        toIndex: AttoKeyIndex,
    ): Collection<AttoWalletAccount> {
        require(fromIndex <= toIndex) { "fromIndex ($fromIndex) must be less than or equal to toIndex ($toIndex)" }
        val indexes =
            buildList {
                var v = fromIndex
                while (v <= toIndex) {
                    add(v)
                    v = AttoKeyIndex(v.value + 1U)
                }
            }
        return openAccount(indexes)
    }

    suspend fun openAccount(index: AttoKeyIndex): AttoWalletAccount = openAccount(index, index).first()

    suspend fun closeAccount(index: AttoKeyIndex) {
        mutex.withLock {
            accounts.remove(index)
        }
    }

    fun addressFlow(): Flow<Set<AttoAddress>> = accounts.addressFlow()

    suspend fun isOpen(index: AttoKeyIndex) = accounts.contains(index)

    suspend fun isOpen(address: AttoAddress) = accounts.contains(address)

    suspend fun getAccount(index: AttoKeyIndex) = accounts.get(index).account

    suspend fun getAccount(address: AttoAddress) = accounts.get(address).account

    suspend fun getAddress(index: AttoKeyIndex) = accounts.get(index).address

    suspend fun publish(
        index: AttoKeyIndex,
        block: AttoBlock,
    ): AttoTransaction {
        val signature = signerProvider.invoke(index).sign(block)
        val work = worker.work(block)

        val transaction = AttoTransaction(block, signature, work)

        client.publish(transaction)

        return transaction
    }

    suspend fun send(
        index: AttoKeyIndex,
        receiverAddress: AttoAddress,
        amount: AttoAmount,
        timestamp: AttoInstant? = null,
    ): AttoTransaction {
        val walletAccount = accounts.get(index)

        return walletAccount.withLock {
            val account = walletAccount.account ?: throw IllegalStateException("Account is not open yet for index $index")

            val timestamp = timestamp ?: client.now()

            val (block, updatedAccount) = account.send(receiverAddress.algorithm, receiverAddress.publicKey, amount, timestamp)

            val transaction = publish(index, block)

            walletAccount.account = updatedAccount

            return@withLock transaction
        }
    }

    suspend fun send(
        address: AttoAddress,
        receiverAddress: AttoAddress,
        amount: AttoAmount,
        timestamp: AttoInstant? = null,
    ): AttoTransaction = send(accounts.get(address).index, receiverAddress, amount, timestamp)

    suspend fun receive(
        receivable: AttoReceivable,
        representativeAddress: AttoAddress? = null,
        timestamp: AttoInstant? = null,
    ): AttoTransaction {
        val walletAccount = accounts.get(receivable.receiverAddress)

        return walletAccount.withLock {
            val account = walletAccount.account
            val timestamp = timestamp ?: client.now()

            val (block, updatedAccount) =
                if (account == null) {
                    require(representativeAddress != null) { "Representative address must be provided for a new account" }
                    AttoAccount.open(representativeAddress.algorithm, representativeAddress.publicKey, receivable, timestamp)
                } else {
                    account.receive(receivable, timestamp)
                }

            val transaction = publish(walletAccount.index, block)

            walletAccount.account = updatedAccount

            return@withLock transaction
        }
    }

    suspend fun change(
        index: AttoKeyIndex,
        representativeAddress: AttoAddress,
        timestamp: AttoInstant? = null,
    ): AttoTransaction {
        val walletAccount = accounts.get(index)

        return walletAccount.withLock {
            val account = walletAccount.account ?: throw IllegalStateException("Account is not open yet for index $index")

            val timestamp = timestamp ?: client.now()

            val (block, updatedAccount) = account.change(representativeAddress.algorithm, representativeAddress.publicKey, timestamp)

            val transaction = publish(index, block)

            walletAccount.account = updatedAccount

            return@withLock transaction
        }
    }

    private class WalletAccounts {
        private val accountAddressMap: MutableMap<AttoAddress, AttoWalletAccount> = mutableMapOf()
        private val accountIndexMap: MutableMap<AttoKeyIndex, AttoWalletAccount> = mutableMapOf()
        private val addressesState = MutableStateFlow<Set<AttoAddress>>(emptySet())

        private val mutex = Mutex()

        suspend fun add(accounts: Collection<AttoWalletAccount>) {
            if (accounts.isEmpty()) return
            mutex.withLock {
                val accounts =
                    accounts.map {
                        if (accountIndexMap.containsKey(it.index)) {
                            throw IllegalArgumentException("Account already exists for index ${it.index}")
                        }
                        it.index to it
                    }

                accountAddressMap += accounts.map { it.second.address to it.second }
                accountIndexMap += accounts.toMap()
                addressesState.value = accountAddressMap.keys.toSet()
            }
        }

        suspend fun add(account: AttoWalletAccount) = add(listOf(account))

        suspend fun contains(index: AttoKeyIndex) = mutex.withLock { accountIndexMap.containsKey(index) }

        suspend fun contains(address: AttoAddress) = mutex.withLock { accountAddressMap.containsKey(address) }

        suspend fun get(index: AttoKeyIndex): AttoWalletAccount =
            mutex.withLock {
                accountIndexMap[index] ?: throw IllegalArgumentException("Account doesn't exist for index $index")
            }

        suspend fun get(address: AttoAddress): AttoWalletAccount =
            mutex.withLock {
                accountAddressMap[address] ?: throw IllegalArgumentException("Account doesn't exist for address $address")
            }

        suspend fun remove(index: AttoKeyIndex) {
            mutex.withLock {
                accountIndexMap.remove(index)?.let {
                    accountAddressMap.remove(it.address)
                }
                addressesState.value = accountAddressMap.keys.toSet()
            }
        }

        suspend fun remove(address: AttoAddress) {
            mutex.withLock {
                accountAddressMap.remove(address)?.let {
                    accountIndexMap.remove(it.index)
                }
                addressesState.value = accountAddressMap.keys.toSet()
            }
        }

        fun addressFlow(): Flow<Set<AttoAddress>> = addressesState.asStateFlow()
    }
}

@JsExportForJs
class AttoWalletAccount(
    val index: AttoKeyIndex,
    val address: AttoAddress,
    @Volatile var account: AttoAccount? = null,
) {
    private val mutex = Mutex()

    internal suspend fun <T> withLock(action: suspend () -> T): T =
        mutex.withLock {
            action.invoke()
        }
}

fun AttoWallet.Companion.create(
    client: AttoNodeClient,
    worker: AttoWorker,
    signerProvider: suspend (AttoKeyIndex) -> AttoSigner,
): AttoWallet = AttoWallet(client, worker, signerProvider)

fun AttoWallet.Companion.create(
    client: AttoNodeClient,
    worker: AttoWorker,
    signerProvider: (AttoKeyIndex) -> AttoSigner,
): AttoWallet = AttoWallet(client, worker, signerProvider)

fun AttoWallet.Companion.create(
    client: AttoNodeClient,
    worker: AttoWorker,
    seed: AttoSeed,
): AttoWallet = AttoWallet(client, worker, seed::toSigner)

fun AttoWallet.Companion.create(
    client: AttoNodeClient,
    worker: AttoWorker,
    signer: AttoSigner,
): AttoWallet =
    AttoWallet(client, worker) {
        require(it == 0U.toAttoIndex()) { "Wallet created from a signer or private key can only have one account" }
        signer
    }

/**
 * Keeps the monitor’s membership aligned with this wallet’s addresses.
 *
 * Behavior
 * - Subscribes to the wallet’s address snapshots and computes diffs over time.
 * - For any new address in the wallet, calls `monitor.monitor(address)`.
 * - For any address removed from the wallet, calls `monitor.stopMonitoring(address)`.
 * - After convergence: `monitor.isMonitored(a) == (a in latest wallet addresses)`.
 *
 * Scope & Lifecycle
 * - Returns a Job; cancel it to stop syncing.
 * - The flow should not fail.
 *
 * Notes
 * - Initial offsets/heights are decided by the monitor’s own `monitor(address)` policy.
 * - Addresses that were never part of this wallet are **not** touched by this binding.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun AttoWallet.bindTo(
    scope: CoroutineScope,
    monitor: AttoAccountMonitor,
): Job =
    addressFlow()
        .runningFold(emptySet<AttoAddress>() to emptySet<AttoAddress>()) { (previous, _), next ->
            next to previous
        }.onEach { (next, previous) ->
            val toAdd = next - previous
            val toRemove = previous - next

            for (address in toAdd) {
                if (!monitor.isMonitored(address)) {
                    monitor.monitor(address)
                }
            }

            for (address in toRemove) {
                if (monitor.isMonitored(address)) {
                    monitor.stopMonitoring(address)
                }
            }
        }.catch { e -> logger.warn(e) { "Bind to monitor failed with $e" } }
        .launchIn(scope)

/**
 * Convenience form of [bindTo] that runs on a dedicated dispatcher.
 *
 * Lifecycle
 * - Returns a Job; cancel it to stop syncing. No parent scope is attached.
 *
 * When to use
 * - Simple CLIs or demos where you don’t have an existing app scope.
 * - For apps with a lifecycle (Android, servers), prefer the scoped overload.
 */
fun AttoWallet.bindTo(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    monitor: AttoAccountMonitor,
): Job {
    val scope = CoroutineScope(dispatcher)
    return bindTo(scope, monitor)
}

/**
 * Automatically receives incoming funds for all wallet addresses and ensures the monitor
 * stays in sync with the wallet while doing so.
 *
 * Behavior
 * - First binds the monitor to the wallet via [bindTo], so membership tracks opens/closes.
 * - Subscribes to `monitor.receivableStream(minAmount)` and calls `receive(...)` for each receivable.
 * - On processing failure, logs and retries after [retryAfter] until cancelled.
 *
 * Scope & Lifecycle
 * - Controlled by [scope]. Cancel the returned Job to stop both the auto-receiver and the binding
 *   (the binding job is cancelled on completion).
 *
 * Assumptions
 * - Receivables emitted by the monitor belong to this wallet’s addresses; if the monitor also includes
 *   foreign addresses, `receive(...)` may fail for those.
 * - When opening a new account on first receive, [defaultRepresentativeAddressProvider] is used.
 */
fun AttoWallet.startAutoReceiver(
    scope: CoroutineScope,
    monitor: AttoAccountMonitor,
    minAmount: AttoAmount = 1UL.toAttoAmount(),
    retryAfter: Duration = 10.seconds,
    defaultRepresentativeAddressProvider: () -> AttoAddress,
): Job {
    val bindJob = bindTo(scope, monitor)
    return monitor
        .receivableStream(minAmount)
        .buffer()
        .onEach {
            receive(it, defaultRepresentativeAddressProvider.invoke())
        }.retryWhen { e, _ ->
            logger.warn(e) { "Failed to collect receivables. Retrying in $retryAfter..." }
            delay(retryAfter)
            true
        }.onCompletion { bindJob.cancel() }
        .launchIn(scope)
}

/**
 * Convenience form of [startAutoReceiver] that runs on a dedicated dispatcher.
 *
 * Lifecycle
 * - Returns a Job; cancel it to stop auto-receiving and its internal binding. No parent scope is attached.
 *
 * When to use
 * - Quick starts and tools without an app-managed scope. For long-running apps, prefer the scoped overload.
 */
fun AttoWallet.startAutoReceiver(
    monitor: AttoAccountMonitor,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    minAmount: AttoAmount = 1UL.toAttoAmount(),
    retryAfter: Duration = 10.seconds,
    defaultRepresentativeAddressProvider: () -> AttoAddress,
): AttoJob {
    val scope = CoroutineScope(dispatcher)
    val job = startAutoReceiver(scope, monitor, minAmount, retryAfter, defaultRepresentativeAddressProvider)
    return AttoJob(job)
}
