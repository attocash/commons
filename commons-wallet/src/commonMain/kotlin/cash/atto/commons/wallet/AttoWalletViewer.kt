package cash.atto.commons.wallet

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoTransaction
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
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class AttoWalletViewer(
    private val publicKey: AttoPublicKey,
    private val client: AttoClient,
    private val transactionRepository: AttoTransactionRepository = AttoTransactionRepository.inMemory(),
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}

    private val retryDelay = 10.seconds

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _accountFlow = client.accounts(publicKey).shareIn(scope, SharingStarted.Eagerly)
    private val accountState = MutableStateFlow<AttoAccount?>(null)
    val accountFlow = accountState.asSharedFlow().filterNotNull()
    val account: AttoAccount? get() = accountState.value

    val receivableFlow = client.receivables(publicKey).shareIn(scope, SharingStarted.Eagerly)

    private val _transactionFlow = MutableSharedFlow<AttoTransaction>()
    val transactionFlow = _transactionFlow.asSharedFlow()

    private fun startAccountStream() {
        scope.launch {
            _accountFlow.collect { newAccount -> update(newAccount) }
        }
    }

    private fun startTransactionStream() {
        scope.launch {
            val fromHeight = transactionRepository.last(publicKey)?.height ?: AttoHeight(1U)
            client.transactions(publicKey, fromHeight).collect {
                _transactionFlow.emit(it)
            }
        }
    }

    private fun startTransactionSaver() {
        scope.launch {
            transactionFlow.collect {
                while (true) {
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

    fun start() {
        startAccountStream()
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

