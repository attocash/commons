package cash.atto.commons.wallet

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoTransaction
import cash.atto.commons.node.AttoNodeOperations
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

class AttoWalletViewer(
    val publicKey: AttoPublicKey,
    private val client: AttoNodeOperations,
    private val accountEntryRepository: AttoAccountEntryRepository? = null,
    private val transactionRepository: AttoTransactionRepository? = null,
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}

    private val retryDelay = 10.seconds

    private val scope = CoroutineScope(Dispatchers.Default)

    private val accountState = MutableStateFlow<AttoAccount?>(null)
    val accountFlow = accountState.asSharedFlow().filterNotNull()
    val account: AttoAccount? get() = accountState.value

    val receivableFlow = client.receivableStream(publicKey).shareIn(scope, SharingStarted.Eagerly)

    private val _accountEntryFlow = MutableSharedFlow<AttoAccountEntry>()
    val accountEntryFlow = _accountEntryFlow.asSharedFlow()

    private val _transactionFlow = MutableSharedFlow<AttoTransaction>()
    val transactionFlow = _transactionFlow.asSharedFlow()

    suspend fun updateAccount() {
        while (coroutineContext.isActive) {
            try {
                val account = client.account(publicKey)
                account?.let {
                    update(it)
                }
                return
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get account $publicKey. Retrying in $retryDelay..." }
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
                    val fromHeight = accountEntryRepository.last(publicKey)?.height ?: AttoHeight(1U)

                    client.accountEntryStream(publicKey, fromHeight).collect {
                        _accountEntryFlow.emit(it)
                    }
                    break
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to stream account entries. Retrying in 10s..." }
                    delay(10_000)
                }
            }
        }
    }

    private fun startAccountEntrySaver() {
        if (accountEntryRepository == null) {
            return
        }
        scope.launch {
            accountEntryFlow.collect {
                while (isActive) {
                    try {
                        accountEntryRepository.save(it)
                        return@collect
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to save $it. Retrying in $retryDelay..." }
                        delay(retryDelay)
                    }
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
                    val fromHeight = transactionRepository.last(publicKey)?.height ?: AttoHeight(1U)
                    client.transactionStream(publicKey, fromHeight).collect {
                        _transactionFlow.emit(it)
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to stream transactions. Retrying in 10s..." }
                    delay(10_000)
                }
            }
        }
    }

    private fun startTransactionSaver() {
        if (transactionRepository == null) {
            return
        }
        scope.launch {
            transactionFlow.collect {
                while (isActive) {
                    try {
                        transactionRepository.save(it)
                        return@collect
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to save $it. Retrying in $retryDelay..." }
                        delay(retryDelay)
                    }
                }
            }
        }
    }

    suspend fun start() {
        updateAccount()
        startAccountEntryStream()
        startAccountEntrySaver()
        startTransactionStream()
        startTransactionSaver()
        logger.info { "Started wallet viewer for $publicKey" }
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
