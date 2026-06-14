package cash.atto.commons.wallet

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoOpenBlock
import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoReceiveBlock
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoSigner
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoVoterWeight
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.isValid
import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.node.AttoNodePublishMismatchException
import cash.atto.commons.node.HeightSearch
import cash.atto.commons.node.TimeDifferenceResponse
import cash.atto.commons.node.monitor.createAccountMonitor
import cash.atto.commons.node.requireMatchingPublish
import cash.atto.commons.toAttoAmount
import cash.atto.commons.toAttoHeight
import cash.atto.commons.toAttoIndex
import cash.atto.commons.toAttoVersion
import cash.atto.commons.toSigner
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.cpu
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AttoWalletValidationTest {
    private val privateKey = AttoPrivateKey.generate()
    private val signer = privateKey.toSigner()

    @Test
    fun `should not publish invalid worker output`() =
        runTest {
            val client = RecordingClient()
            val block = sampleBlock()
            val invalidWork =
                generateSequence { AttoWork(Random.nextBytes(8)) }
                    .first { !it.isValid(block) }
            val wallet = AttoWallet(client, StaticWorker(invalidWork)) { signer }

            assertFailsWith<IllegalStateException> {
                wallet.publish(0U.toAttoIndex(), block)
            }
            assertTrue(client.published.isEmpty())
        }

    @Test
    fun `should not publish invalid signer output`() =
        runTest {
            val client = RecordingClient()
            val block = sampleBlock()
            val wallet = AttoWallet(client, CpuWorker()) { InvalidSigner(signer) }

            assertFailsWith<IllegalStateException> {
                wallet.publish(0U.toAttoIndex(), block)
            }
            assertTrue(client.published.isEmpty())
        }

    @Test
    fun `auto receiver should skip foreign receivable and process wallet receivable`() =
        runTest {
            val foreignReceivable = receivable(AttoPublicKey(Random.nextBytes(32)))
            val walletReceivable = receivable(signer.publicKey)
            val client = RecordingClient(receivables = listOf(foreignReceivable, walletReceivable))
            val wallet = AttoWallet(client, CooperativeWorker()) { signer }
            val monitor = client.createAccountMonitor()

            wallet.openAccount(0U.toAttoIndex())
            val job =
                wallet.startAutoReceiver(backgroundScope, monitor, retryAfter = 100.milliseconds) {
                    AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.nextBytes(32)))
                }
            withTimeout(10.seconds) {
                while (!monitor.isMonitored(signer.address)) {
                    delay(100.milliseconds)
                }
            }

            withTimeout(10.seconds) {
                while (client.published.isEmpty()) {
                    delay(100.milliseconds)
                }
            }
            job.cancel()

            assertEquals(1, client.published.size)
            assertEquals(walletReceivable.hash, (client.published.single().block as AttoOpenBlock).sendHash)
        }

    @Test
    fun `should not advance account when publish acknowledgement mismatches`() =
        runTest {
            val account = account(signer.publicKey)
            val mismatchBlock = sampleBlock()
            val client =
                RecordingClient(
                    accounts = listOf(account),
                    publishResponse = { transaction -> AttoTransaction(mismatchBlock, transaction.signature, transaction.work) },
                )
            val wallet = AttoWallet(client, CpuWorker()) { signer }
            wallet.openAccount(0U.toAttoIndex())

            assertFailsWith<AttoNodePublishMismatchException> {
                wallet.send(
                    0U.toAttoIndex(),
                    AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.nextBytes(32))),
                    1UL.toAttoAmount(),
                )
            }

            assertEquals(account, wallet.getAccount(0U.toAttoIndex()))
        }

    private fun sampleBlock(): AttoReceiveBlock =
        AttoReceiveBlock(
            version = 0U.toAttoVersion(),
            network = AttoNetwork.LOCAL,
            algorithm = AttoAlgorithm.V1,
            publicKey = signer.publicKey,
            height = 2U.toAttoHeight(),
            balance = AttoAmount.MAX,
            timestamp = AttoInstant.now(),
            previous = AttoHash(Random.nextBytes(ByteArray(32))),
            sendHashAlgorithm = AttoAlgorithm.V1,
            sendHash = AttoHash(Random.nextBytes(ByteArray(32))),
        )

    private fun receivable(receiverPublicKey: AttoPublicKey): AttoReceivable =
        AttoReceivable(
            network = AttoNetwork.LOCAL,
            hash = AttoHash(Random.nextBytes(32)),
            version = 0U.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            publicKey = AttoPublicKey(Random.nextBytes(32)),
            timestamp = AttoInstant.now(),
            receiverAlgorithm = AttoAlgorithm.V1,
            receiverPublicKey = receiverPublicKey,
            amount = 1UL.toAttoAmount(),
        )

    private fun account(publicKey: AttoPublicKey): AttoAccount =
        AttoAccount(
            publicKey = publicKey,
            network = AttoNetwork.LOCAL,
            version = 0U.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            height = 1U.toAttoHeight(),
            balance = AttoAmount.MAX,
            lastTransactionHash = AttoHash(Random.nextBytes(32)),
            lastTransactionTimestamp = AttoInstant.now(),
            representativeAlgorithm = AttoAlgorithm.V1,
            representativePublicKey = AttoPublicKey(Random.nextBytes(32)),
        )

    private class StaticWorker(
        private val work: AttoWork,
    ) : AttoWorker {
        override suspend fun work(
            threshold: ULong,
            target: AttoWorkTarget,
        ): AttoWork = work

        override fun close() {
        }
    }

    private class CpuWorker : AttoWorker {
        override suspend fun work(
            threshold: ULong,
            target: AttoWorkTarget,
        ): AttoWork = AttoWorker.cpu().use { it.work(threshold, target) }

        override fun close() {
        }
    }

    private class CooperativeWorker : AttoWorker {
        override suspend fun work(
            threshold: ULong,
            target: AttoWorkTarget,
        ): AttoWork {
            val work = ByteArray(AttoWork.SIZE)
            var iterationsUntilYield = 4_096
            while (true) {
                if (AttoWork.isValid(threshold, target.value, work)) {
                    return AttoWork(work.copyOf())
                }
                incrementByteArray(work)
                iterationsUntilYield--
                if (iterationsUntilYield == 0) {
                    iterationsUntilYield = 4_096
                    yield()
                }
            }
        }

        override fun close() {
        }

        private fun incrementByteArray(byteArray: ByteArray) {
            var carry = 1
            for (i in byteArray.indices) {
                val sum = (byteArray[i].toInt() and 0xFF) + carry
                byteArray[i] = sum.toByte()
                carry = if (sum > 0xFF) 1 else 0
                if (carry == 0) break
            }
        }
    }

    private class InvalidSigner(
        private val delegate: AttoSigner,
    ) : AttoSigner {
        override val algorithm = delegate.algorithm
        override val publicKey = delegate.publicKey
        override val address = delegate.address

        override suspend fun sign(hash: AttoHash): AttoSignature = AttoSignature(Random.nextBytes(64))
    }

    private class RecordingClient(
        accounts: Collection<AttoAccount> = emptyList(),
        private val receivables: Collection<AttoReceivable> = emptyList(),
        private val publishResponse: (AttoTransaction) -> AttoTransaction = { it },
    ) : AttoNodeClient {
        private val accounts = accounts.associateBy { it.address }
        val published = mutableListOf<AttoTransaction>()

        override suspend fun account(publicKey: AttoPublicKey): AttoAccount? = accounts[AttoAddress(AttoAlgorithm.V1, publicKey)]

        override suspend fun account(addresses: Collection<AttoAddress>): Collection<AttoAccount> = addresses.mapNotNull { accounts[it] }

        override fun accountStream(): Flow<AttoAccount> = emptyFlow()

        override fun accountStream(publicKey: AttoPublicKey): Flow<AttoAccount> = emptyFlow()

        override fun accountStream(addresses: Collection<AttoAddress>): Flow<AttoAccount> = emptyFlow()

        override fun receivableStream(
            publicKey: AttoPublicKey,
            minAmount: AttoAmount,
        ): Flow<AttoReceivable> = emptyFlow()

        override fun receivableStream(
            addresses: Collection<AttoAddress>,
            minAmount: AttoAmount,
        ): Flow<AttoReceivable> =
            flow {
                receivables.forEach { emit(it) }
            }

        override suspend fun accountEntry(hash: AttoHash): AttoAccountEntry = throw UnsupportedOperationException()

        override fun accountEntryStream(): Flow<AttoAccountEntry> = emptyFlow()

        override fun accountEntryStream(hash: AttoHash): Flow<AttoAccountEntry> = emptyFlow()

        override fun accountEntryStream(
            publicKey: AttoPublicKey,
            fromHeight: AttoHeight,
            toHeight: AttoHeight?,
        ): Flow<AttoAccountEntry> = emptyFlow()

        override fun accountEntryStream(search: HeightSearch): Flow<AttoAccountEntry> = emptyFlow()

        override suspend fun transaction(hash: AttoHash): AttoTransaction = throw UnsupportedOperationException()

        override fun transactionStream(): Flow<AttoTransaction> = emptyFlow()

        override fun transactionStream(hash: AttoHash): Flow<AttoTransaction> = emptyFlow()

        override fun transactionStream(
            publicKey: AttoPublicKey,
            fromHeight: AttoHeight,
            toHeight: AttoHeight?,
        ): Flow<AttoTransaction> = emptyFlow()

        override fun transactionStream(search: HeightSearch): Flow<AttoTransaction> = emptyFlow()

        override suspend fun now(currentTime: AttoInstant): TimeDifferenceResponse = TimeDifferenceResponse(currentTime, currentTime, 0)

        override suspend fun publish(transaction: AttoTransaction) {
            published += transaction
            requireMatchingPublish(transaction, publishResponse(transaction))
        }

        override suspend fun voterWeight(address: AttoAddress): AttoVoterWeight = throw UnsupportedOperationException()
    }
}
