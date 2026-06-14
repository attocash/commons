package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlockType
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoVoterWeight
import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.node.HeightSearch
import cash.atto.commons.node.TimeDifferenceResponse
import cash.atto.commons.toAttoHeight
import cash.atto.commons.toAttoVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class AttoMonitorFilteringTest {
    private val trackedPublicKey = AttoPublicKey(Random.nextBytes(32))
    private val foreignPublicKey = AttoPublicKey(Random.nextBytes(32))
    private val trackedAddress = AttoAddress(AttoAlgorithm.V1, trackedPublicKey)
    private val foreignAddress = AttoAddress(AttoAlgorithm.V1, foreignPublicKey)

    @Test
    fun `account monitor should filter foreign accounts`() =
        runTest {
            val tracked = account(trackedPublicKey, 1U)
            val foreign = account(foreignPublicKey, 1U)
            val monitor = RecordingClient(accounts = listOf(foreign, tracked)).createAccountMonitor()
            monitor.monitor(trackedAddress)

            assertEquals(listOf(tracked), monitor.accountStream().take(1).toList())
        }

    @Test
    fun `height monitor should filter foreign values`() =
        runTest {
            val tracked = accountEntry(trackedPublicKey, 1U)
            val foreign = accountEntry(foreignPublicKey, 1U)
            val monitor = RecordingClient(accountEntries = listOf(foreign, tracked)).createAccountMonitor()
            monitor.monitor(trackedAddress)

            val values =
                monitor
                    .toAccountEntryMonitor()
                    .stream()
                    .take(1)
                    .toList()
                    .map { it.value }

            assertEquals(listOf(tracked), values)
        }

    private fun account(
        publicKey: AttoPublicKey,
        height: UInt,
    ): AttoAccount =
        AttoAccount(
            publicKey = publicKey,
            network = AttoNetwork.LOCAL,
            version = 0U.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            height = height.toAttoHeight(),
            balance = AttoAmount.MAX,
            lastTransactionHash = AttoHash(Random.nextBytes(32)),
            lastTransactionTimestamp = AttoInstant.now(),
            representativeAlgorithm = AttoAlgorithm.V1,
            representativePublicKey = trackedPublicKey,
        )

    private fun accountEntry(
        publicKey: AttoPublicKey,
        height: UInt,
    ): AttoAccountEntry =
        AttoAccountEntry(
            hash = AttoHash(Random.nextBytes(32)),
            algorithm = AttoAlgorithm.V1,
            publicKey = publicKey,
            height = height.toAttoHeight(),
            blockType = AttoBlockType.OPEN,
            subjectAlgorithm = AttoAlgorithm.V1,
            subjectPublicKey = publicKey,
            previousBalance = AttoAmount.MIN,
            balance = AttoAmount.MAX,
            timestamp = AttoInstant.now(),
        )

    private class RecordingClient(
        private val accounts: Collection<AttoAccount> = emptyList(),
        private val accountEntries: Collection<AttoAccountEntry> = emptyList(),
    ) : AttoNodeClient {
        override suspend fun account(publicKey: AttoPublicKey): AttoAccount? = accounts.firstOrNull { it.publicKey == publicKey }

        override suspend fun account(addresses: Collection<AttoAddress>): Collection<AttoAccount> = accounts

        override fun accountStream(): Flow<AttoAccount> = accounts.asFlow()

        override fun accountStream(publicKey: AttoPublicKey): Flow<AttoAccount> = accounts.asFlow()

        override fun accountStream(addresses: Collection<AttoAddress>): Flow<AttoAccount> = accounts.asFlow()

        override fun receivableStream(
            publicKey: AttoPublicKey,
            minAmount: AttoAmount,
        ): Flow<AttoReceivable> = emptyFlow()

        override fun receivableStream(
            addresses: Collection<AttoAddress>,
            minAmount: AttoAmount,
        ): Flow<AttoReceivable> = emptyFlow()

        override suspend fun accountEntry(hash: AttoHash): AttoAccountEntry = throw UnsupportedOperationException()

        override fun accountEntryStream(): Flow<AttoAccountEntry> = accountEntries.asFlow()

        override fun accountEntryStream(hash: AttoHash): Flow<AttoAccountEntry> = accountEntries.asFlow()

        override fun accountEntryStream(
            publicKey: AttoPublicKey,
            fromHeight: AttoHeight,
            toHeight: AttoHeight?,
        ): Flow<AttoAccountEntry> = accountEntries.asFlow()

        override fun accountEntryStream(search: HeightSearch): Flow<AttoAccountEntry> = accountEntries.asFlow()

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
        }

        override suspend fun voterWeight(address: AttoAddress): AttoVoterWeight = throw UnsupportedOperationException()
    }
}
