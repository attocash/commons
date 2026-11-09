package cash.atto.commons.wallet

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoTransaction
import cash.atto.commons.node.AttoNodeOperations
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

@Deprecated("Use AttoAccountMonitor instead")
class AttoWalletViewer internal constructor(
    val address: AttoAddress,
    private val client: AttoNodeOperations,
    private val accountEntryRepository: AttoAccountEntryRepository?,
    private val transactionRepository: AttoTransactionRepository?,
    private val scope: CoroutineScope,
) : AutoCloseable {
    constructor(
        address: AttoAddress,
        client: AttoNodeOperations,
        accountEntryRepository: AttoAccountEntryRepository? = null,
        transactionRepository: AttoTransactionRepository? = null,
    ) : this(address, client, accountEntryRepository, transactionRepository, CoroutineScope(Dispatchers.Default + SupervisorJob()))

    private val logger = KotlinLogging.logger {}

    private val retryDelay = 10.seconds

    private val accountState = MutableStateFlow<AttoAccount?>(null)
    val accountFlow = accountState.asSharedFlow().filterNotNull()
    val account: AttoAccount? get() = accountState.value

    val receivableFlow = client.receivableStream(address)

    private val _accountEntryFlow = MutableSharedFlow<AttoAccountEntry>()
    val accountEntryFlow = _accountEntryFlow.asSharedFlow()

    private val _transactionFlow = MutableSharedFlow<AttoTransaction>()
    val transactionFlow = _transactionFlow.asSharedFlow()

    suspend fun updateAccount() {
        while (coroutineContext.isActive) {
            try {
                val account = client.account(address)
                account?.let {
                    update(it)
                }
                return
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get account $address. Retrying in $retryDelay..." }
                delay(retryDelay)
            }
        }
    }

    private fun startAccountEntryStream() {
        if (accountEntryRepository == null) {
            logger.info { "No account entry repository defined. Account Entry stream won't start" }
            return
        }

        scope.launch {
            while (isActive) {
                try {
                    val fromHeight = accountEntryRepository.last(address)?.height ?: AttoHeight(0U)

                    client.accountEntryStream(address, fromHeight + 1U).collect {
                        saveWithRetry(it, accountEntryRepository::save)
                        _accountEntryFlow.emit(it)
                    }
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to stream account entries. Retrying in 10s..." }
                    delay(10_000)
                }
            }
        }
    }

    private fun startTransactionStream() {
        if (transactionRepository == null) {
            logger.info { "No transaction repository defined. Transaction stream won't start" }
            return
        }
        scope.launch {
            while (isActive) {
                try {
                    val fromHeight = transactionRepository.last(address)?.height ?: AttoHeight(0U)

                    client.transactionStream(address, fromHeight + 1U).collect {
                        saveWithRetry(it, transactionRepository::save)
                        _transactionFlow.emit(it)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to stream transactions. Retrying in 10s..." }
                    delay(10_000)
                }
            }
        }
    }

    private suspend fun <T> saveWithRetry(
        value: T,
        save: suspend (T) -> Unit,
    ) {
        while (true) {
            try {
                save(value)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to save $value. Retrying in $retryDelay..." }
                delay(retryDelay)
            }
        }
    }

    suspend fun start() {
        updateAccount()
        startAccountEntryStream()
        startTransactionStream()
        logger.info { "Started wallet viewer for $address" }
    }

    internal fun update(newAccount: AttoAccount) {
        accountState.update { account ->
            if (account == null || account.height < newAccount.height) {
                newAccount
            } else {
                account
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}
