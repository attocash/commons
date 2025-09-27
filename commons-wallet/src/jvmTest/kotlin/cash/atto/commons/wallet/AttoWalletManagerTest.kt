package cash.atto.commons.wallet

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlockType
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoReceiveBlock
import cash.atto.commons.AttoTransaction
import cash.atto.commons.ReceiveSupport
import cash.atto.commons.gatekeeper.AttoAuthenticator
import cash.atto.commons.gatekeeper.AttoMockGatekeeper
import cash.atto.commons.gatekeeper.custom
import cash.atto.commons.gatekeeper.toHeaderProvider
import cash.atto.commons.node.AttoMockNode
import cash.atto.commons.node.AttoMockWorker
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.commons.node.custom
import cash.atto.commons.sign
import cash.atto.commons.toAttoAmount
import cash.atto.commons.toAttoHeight
import cash.atto.commons.toAttoVersion
import cash.atto.commons.toPublicKey
import cash.atto.commons.toSigner
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.cpu
import cash.atto.commons.worker.remote
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
class AttoWalletManagerTest {
    companion object {
        private val defaultTimeout = 20.seconds
        private val mockNode = AttoMockNode()
        private val mockWorker = AttoMockWorker()
        private val mockGatekeeper = AttoMockGatekeeper()

        private val privateKey = AttoPrivateKey.generate()
        private val publicKey = privateKey.toPublicKey()
        private val signer = privateKey.toSigner()
        private val authenticator = AttoAuthenticator.custom("http://localhost:${mockGatekeeper.port}", signer)
        private val client =
            AttoNodeOperations.custom(
                AttoNetwork.LOCAL,
                "http://localhost:${mockNode.port}",
                authenticator.toHeaderProvider(),
            )

        private val transactionRepository = AttoTransactionRepository.inMemory()
        private val accountEntryRepository = AttoAccountEntryRepository.inMemory()
        private val viewer = AttoWalletViewer(publicKey, client, accountEntryRepository, transactionRepository)
        private val worker = AttoWorker.remote("http://localhost:${mockWorker.port}")
        private val workCache = AttoWorkCache.inMemory()
        private val walletManager =
            AttoWalletManager(viewer, signer, client, worker, workCache) {
                AttoAddress(AttoAlgorithm.V1, publicKey)
            }

        init {
            mockNode.start()
            mockWorker.start()
            mockGatekeeper.start()

            runBlocking {
                walletManager.start()
            }
        }
    }

    @Test
    fun `should stream account`(): Unit =
        runBlocking {
            // given
            val expectedAccount = AttoAccount.sample()

            // when
            mockNode.accountMap[expectedAccount.publicKey] = expectedAccount

            // then
            val account =
                walletManager
                    .accountFlow
                    .onStart { walletManager.updateAccount() }
                    .timeout(defaultTimeout)
                    .first { it.publicKey == expectedAccount.publicKey }
            assertNotNull(account)
        }

    @Test
    fun `should stream account entry`() =
        runBlocking {
            // given
            val expectedEntry = AttoAccountEntry.sample()

            // when
            val streamedEntry =
                walletManager
                    .accountEntryFlow
                    .onStart {
                        mockNode.accountEntryFlow.emit(expectedEntry)
                    }.timeout(defaultTimeout)
                    .first()

            // then
            assertEquals(expectedEntry, streamedEntry)
            val repositoryEntry =
                accountEntryRepository.filterOrNullWhenTimeout {
                    it == expectedEntry
                }
            assertEquals(expectedEntry, repositoryEntry)
        }

    @Test
    fun `should stream transaction`() =
        runBlocking {
            // given
            val expectedTransaction = AttoTransaction.sample()

            // when
            val streamedTransaction =
                walletManager
                    .transactionFlow
                    .onStart {
                        mockNode.transactionFlow.emit(expectedTransaction)
                    }.timeout(defaultTimeout)
                    .first { it == expectedTransaction }

            // then
            assertEquals(expectedTransaction, streamedTransaction)
            val repositoryTransaction =
                transactionRepository
                    .stream(publicKey)
                    .timeout(defaultTimeout)
                    .firstOrNull { it == expectedTransaction }
            assertEquals(expectedTransaction, repositoryTransaction)
        }

    @Test
    fun `should stream receivable`() =
        runBlocking {
            // given
            val expectedReceivable = AttoReceivable.sample()

            // when
            val transaction =
                walletManager
                    .receivableFlow
                    .onStart {
                        mockNode.receivableFlow.emit(expectedReceivable)
                    }.timeout(defaultTimeout)
                    .first { expectedReceivable == it }

            // then
            assertEquals(expectedReceivable, transaction)
        }

    @Test
    fun `should send blocks`() =
        runBlocking {
            val firstReceivable = AttoReceivable.sample() // open

            val firstTransaction =
                transactionRepository
                    .stream(publicKey)
                    .onStart {
                        mockNode.receivableFlow.emit(firstReceivable)
                    }.timeout(defaultTimeout)
                    .first {
                        val block = it.block
                        block is ReceiveSupport && block.sendHash == firstReceivable.hash
                    }

            assertNotNull(firstTransaction)

            val secondReceivable = AttoReceivable.sample() // receive

            val secondTransaction =
                transactionRepository
                    .stream(publicKey)
                    .onStart {
                        mockNode.receivableFlow.emit(secondReceivable)
                    }.timeout(defaultTimeout)
                    .first {
                        val block = it.block
                        block is ReceiveSupport && block.sendHash == secondReceivable.hash
                    }

            assertNotNull(secondTransaction)

            walletManager.send(AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32))), AttoAmount(1UL))
            walletManager.change(AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32))))

            println()
        }

    private suspend fun AttoAccountEntryRepository.filterOrNullWhenTimeout(filter: (AttoAccountEntry) -> Boolean): AttoAccountEntry? {
        return withTimeoutOrNull(defaultTimeout) {
            do {
                val entry =
                    stream(publicKey)
                        .firstOrNull {
                            filter.invoke(it)
                        }
                if (entry != null) {
                    return@withTimeoutOrNull entry
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
            lastTransactionTimestamp = AttoInstant.now(),
            representativeAlgorithm = AttoAlgorithm.V1,
            representativePublicKey = AttoPublicKey(Random.Default.nextBytes(32)),
        )
    }

    private fun AttoAccountEntry.Companion.sample(): AttoAccountEntry {
        return AttoAccountEntry(
            hash = AttoHash(Random.Default.nextBytes(32)),
            algorithm = AttoAlgorithm.V1,
            publicKey = publicKey,
            height = 2U.toAttoHeight(),
            blockType = AttoBlockType.RECEIVE,
            subjectAlgorithm = AttoAlgorithm.V1,
            subjectPublicKey = publicKey,
            previousBalance = AttoAmount.MIN,
            balance = AttoAmount.MAX,
            timestamp = AttoInstant.now(),
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
                timestamp = AttoInstant.now(),
                previous = AttoHash(Random.nextBytes(ByteArray(32))),
                sendHashAlgorithm = AttoAlgorithm.V1,
                sendHash = AttoHash(Random.nextBytes(ByteArray(32))),
            )

        return AttoTransaction(
            block = receiveBlock,
            signature = runBlocking { privateKey.sign(receiveBlock.hash) },
            work = runBlocking { AttoWorker.cpu().work(receiveBlock) },
        )
    }

    private fun AttoReceivable.Companion.sample(): AttoReceivable =
        AttoReceivable(
            hash = AttoHash(Random.nextBytes(32)),
            version = 0U.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            publicKey = AttoPublicKey(Random.nextBytes(32)),
            timestamp = AttoInstant.now(),
            receiverAlgorithm = AttoAlgorithm.V1,
            receiverPublicKey = publicKey,
            amount = 1000UL.toAttoAmount(),
        )
}
