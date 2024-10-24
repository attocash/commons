package cash.atto.commons.wallet

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoReceiveBlock
import cash.atto.commons.AttoTransaction
import cash.atto.commons.ReceiveSupport
import cash.atto.commons.gatekeeper.AttoAuthenticator
import cash.atto.commons.gatekeeper.custom
import cash.atto.commons.sign
import cash.atto.commons.toAttoAmount
import cash.atto.commons.toAttoHeight
import cash.atto.commons.toAttoVersion
import cash.atto.commons.toPublicKey
import cash.atto.commons.toSigner
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.cpu
import cash.atto.commons.worker.remote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

expect fun randomPort(): Int

class AttoWalletManagerTest {
    companion object {
        private val port = randomPort()
        private val backend = AttoTestBackend(port)
        private val privateKey = AttoPrivateKey.generate()
        private val publicKey = privateKey.toPublicKey()
        private val signer = privateKey.toSigner()
        private val authenticator = AttoAuthenticator.custom("http://localhost:$port", signer)
        private val client = AttoNodeClient.attoBackend(AttoNetwork.LOCAL, "http://localhost:$port", authenticator)
        private val transactionRepository = AttoTransactionRepository.inMemory()
        private val viewer = AttoWalletViewer(publicKey, client, transactionRepository)
        private val worker = AttoWorker.remote("http://localhost:$port")
        private val workCache = AttoWorkCache.inMemory()
        private val walletManager = AttoWalletManager(viewer, signer, client, worker, workCache) {
            AttoAddress(AttoAlgorithm.V1, publicKey)
        }

        init {
            backend.start()
            walletManager.start()
        }
    }

    @Test
    fun `should stream account`(): Unit = runBlocking {
        // given
        val expectedAccount = AttoAccount.sample()

        // when
        backend.accountMap[expectedAccount.publicKey] = expectedAccount

        // then
        val account = withTimeoutOrNull(5.seconds) {
            walletManager.accountFlow
                .onStart { viewer.updateAccount() }
                .first { it.publicKey == expectedAccount.publicKey }
        }
        assertNotNull(account)
    }

    @Test
    fun `should stream transaction`() = runBlocking {
        // given
        val expectedTransaction = AttoTransaction.sample()

        // when
        backend.transactionFlow.emit(expectedTransaction)

        // then
        val streamedTransaction = withTimeoutOrNull(5.seconds) { walletManager.transactionFlow.first() }
        assertEquals(expectedTransaction, streamedTransaction)
        val repositoryTransaction = filterOrNullWhenTimeout {
            it == expectedTransaction
        }
        assertEquals(expectedTransaction, repositoryTransaction)
    }

    @Test
    fun `should stream receivable`() = runBlocking {
        // given
        val expectedReceivable = AttoReceivable.sample()

        // when
        backend.receivableFlow.emit(expectedReceivable)

        // then
        val transaction = withTimeoutOrNull(5.seconds) { walletManager.receivableFlow.first { expectedReceivable == it } }
        assertEquals(expectedReceivable, transaction)
    }

    @Test
    fun `should send blocks`() = runBlocking {
        val firstReceivable = AttoReceivable.sample() // open
        backend.receivableFlow.emit(firstReceivable)
        val firstTransaction = filterOrNullWhenTimeout {
            val block = it.block
            block is ReceiveSupport && block.sendHash == firstReceivable.hash
        }
        assertNotNull(firstTransaction)

        val secondReceivable = AttoReceivable.sample() // receive
        backend.receivableFlow.emit(secondReceivable)
        val secondTransaction = filterOrNullWhenTimeout {
            val block = it.block
            block is ReceiveSupport && block.sendHash == secondReceivable.hash
        }
        assertNotNull(secondTransaction)

        walletManager.send(AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32))), AttoAmount(1UL))
        walletManager.change(AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32))))
    }

    private suspend fun filterOrNullWhenTimeout(filter: (AttoTransaction) -> Boolean): AttoTransaction? {
        return withTimeoutOrNull(5.seconds) {
            do {
                val transaction = transactionRepository.list(publicKey)
                    .firstOrNull {
                        filter.invoke(it)
                    }
                if (transaction != null) {
                    return@withTimeoutOrNull transaction
                }
            } while (isActive)
            null
        }
    }


    private fun AttoAccount.Companion.sample(): AttoAccount {
        return AttoAccount(
            publicKey = publicKey,
            network = AttoNetwork.LOCAL,
            version = 0U.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            height = 1U.toAttoHeight(),
            balance = AttoAmount(ULong.MAX_VALUE / 2U),
            lastTransactionHash = AttoHash(Random.Default.nextBytes(32)),
            lastTransactionTimestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
            representativeAlgorithm = AttoAlgorithm.V1,
            representativePublicKey = AttoPublicKey(Random.Default.nextBytes(32)),
        )
    }

    private fun AttoTransaction.Companion.sample(): AttoTransaction {
        val receiveBlock =
            AttoReceiveBlock(
                version = 0U.toAttoVersion(),
                network = AttoNetwork.LOCAL,
                algorithm = AttoAlgorithm.V1,
                publicKey = publicKey,
                height = 2U.toAttoHeight(),
                balance = AttoAmount.MAX,
                timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
                previous = AttoHash(Random.nextBytes(ByteArray(32))),
                sendHashAlgorithm = AttoAlgorithm.V1,
                sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32))),
            )

        return AttoTransaction(
            block = receiveBlock,
            signature = runBlocking { privateKey.sign(receiveBlock.hash) },
            work = runBlocking { AttoWorker.cpu().work(receiveBlock) },
        )
    }

    private fun AttoReceivable.Companion.sample(): AttoReceivable {
        return AttoReceivable(
            version = 0U.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            receiverAlgorithm = AttoAlgorithm.V1,
            receiverPublicKey = publicKey,
            amount = 1000UL.toAttoAmount(),
            timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
            hash = AttoHash(Random.Default.nextBytes(32))
        )
    }
}
