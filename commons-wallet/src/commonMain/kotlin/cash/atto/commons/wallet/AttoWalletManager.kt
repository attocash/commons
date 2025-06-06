package cash.atto.commons.wallet

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoChangeBlock
import cash.atto.commons.AttoOpenBlock
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoSendBlock
import cash.atto.commons.AttoSigner
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoWork
import cash.atto.commons.PreviousSupport
import cash.atto.commons.isValid
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.commons.toHex
import cash.atto.commons.worker.AttoWorker
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

class AttoWalletManager(
    private val viewer: AttoWalletViewer,
    private val signer: AttoSigner,
    private val client: AttoNodeOperations,
    private val worker: AttoWorker,
    private val workCache: AttoWorkCache = AttoWorkCache.inMemory(),
    private val representativeProvider: () -> AttoAddress,
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}

    private val retryDelay = 10.seconds

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _readyState = MutableStateFlow(false)
    val readyState = _readyState.asStateFlow()

    private val mutex = Mutex()

    val publicKey = viewer.publicKey
    val account: AttoAccount? get() = viewer.account

    val accountFlow = viewer.accountFlow
    val accountEntryFlow = viewer.accountEntryFlow
    val receivableFlow = viewer.receivableFlow
    val transactionFlow = viewer.transactionFlow

    suspend fun updateAccount() {
        viewer.updateAccount()
    }

    private suspend fun work(
        timestamp: Instant,
        target: ByteArray,
    ): AttoWork {
        while (coroutineContext.isActive) {
            try {
                val work = workCache.get()
                if (work?.isValid(timestamp, target) == true) {
                    return work
                }

                val newWork = worker.work(client.network, timestamp, target)
                workCache.save(newWork)
                return newWork
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get work for $timestamp target ${target.toHex()}. Retrying in $retryDelay..." }
                delay(retryDelay)
            }
        }
        throw CancellationException("Get work scope was cancelled.")
    }

    private fun startWorkCacher() {
        scope.launch {
            accountFlow.collect {
                mutex.withLock {
                    work(Clock.System.now(), it.lastTransactionHash.value)
                }
            }
        }
    }

    private fun startAutoReceiver() {
        scope.launch {
            while (isActive) {
                try {
                    receivableFlow.collect { receivable ->
                        receive(receivable)
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to collect receivables. Retrying in $retryDelay..." }
                    delay(retryDelay)
                }
            }
        }
    }

    suspend fun start(autoReceive: Boolean = true) {
        viewer.start()

        startWorkCacher()

        if (autoReceive) {
            startAutoReceiver()
        }

        _readyState.value = true
        logger.info { "Started wallet manager for ${signer.publicKey}" }
    }

    private fun requireReady() {
        require(readyState.value) { "Wallet is not ready yet" }
    }

    private fun getAccountOrThrow(): AttoAccount {
        val account = this.account
        require(account != null) { "Account is not open yet" }
        return account
    }

    private suspend fun publish(
        block: AttoBlock,
        newAccount: AttoAccount,
    ): AttoTransaction {
        val target =
            when (block) {
                is AttoOpenBlock -> block.publicKey.value
                is PreviousSupport -> block.previous.value
                else -> throw IllegalArgumentException("$block unsupported")
            }

        val transaction =
            AttoTransaction(
                block = block,
                signature = signer.sign(block),
                work = work(block.timestamp, target),
            )

        client.publish(transaction)
        viewer.update(newAccount)

        return transaction
    }

    suspend fun receive(
        receivable: AttoReceivable,
        timestamp: Instant? = null,
    ): AttoBlock {
        requireReady()

        mutex.withLock {
            val blockTimestamp = timestamp ?: client.now()
            val account = this.account
            val (block, newAccount) =
                if (account == null) {
                    val algorithmPublicKey = representativeProvider.invoke()
                    AttoAccount.open(algorithmPublicKey.algorithm, algorithmPublicKey.publicKey, receivable, client.network, blockTimestamp)
                } else {
                    account.receive(receivable, blockTimestamp)
                }

            publish(block, newAccount)

            return block
        }
    }

    suspend fun send(
        receiverAddress: AttoAddress,
        amount: AttoAmount,
        timestamp: Instant? = null,
    ): AttoSendBlock {
        requireReady()
        require(receiverAddress.publicKey != publicKey) { "You can't send $amount to yourself" }

        mutex.withLock {
            val account = getAccountOrThrow()

            if (amount > account.balance) {
                throw IllegalStateException("${account.balance} balance is not enough to send $amount")
            }

            val blockTimestamp = timestamp ?: client.now()
            val (block, newAccount) = account.send(receiverAddress.algorithm, receiverAddress.publicKey, amount, blockTimestamp)

            publish(block, newAccount)

            return block
        }
    }

    suspend fun change(
        representativeAddress: AttoAddress,
        timestamp: Instant? = null,
    ): AttoChangeBlock {
        requireReady()

        mutex.withLock {
            val account = getAccountOrThrow()

            val blockTimestamp = timestamp ?: client.now()
            val (block, newAccount) = account.change(representativeAddress.algorithm, representativeAddress.publicKey, blockTimestamp)

            publish(block, newAccount)

            return block
        }
    }

    override fun close() {
        scope.cancel()
        viewer.close()
    }

    private fun AttoWork.isValid(
        timestamp: Instant,
        target: ByteArray,
    ): Boolean =
        AttoWork.isValid(
            client.network,
            timestamp,
            target,
            this.value,
        )
}
