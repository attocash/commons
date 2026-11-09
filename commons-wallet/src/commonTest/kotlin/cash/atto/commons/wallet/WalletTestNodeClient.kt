package cash.atto.commons.wallet

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
import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoVoterWeight
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.node.HeightSearch
import cash.atto.commons.node.TimeDifferenceResponse
import cash.atto.commons.toAttoHeight
import cash.atto.commons.toAttoVersion
import cash.atto.commons.worker.AttoWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.yield

internal open class WalletTestNodeClient(
    private val accounts: Collection<AttoAccount> = emptyList(),
) : AttoNodeClient {
    override suspend fun account(address: AttoAddress): AttoAccount? = accounts.firstOrNull { it.address == address }

    override suspend fun account(addresses: Collection<AttoAddress>): Collection<AttoAccount> = accounts.filter { it.address in addresses }

    override fun accountStream(): Flow<AttoAccount> = emptyFlow()

    override fun accountStream(address: AttoAddress): Flow<AttoAccount> = emptyFlow()

    override fun accountStream(addresses: Collection<AttoAddress>): Flow<AttoAccount> = emptyFlow()

    override fun receivableStream(
        address: AttoAddress,
        minAmount: AttoAmount,
    ): Flow<AttoReceivable> = emptyFlow()

    override fun receivableStream(
        addresses: Collection<AttoAddress>,
        minAmount: AttoAmount,
    ): Flow<AttoReceivable> = emptyFlow()

    override suspend fun accountEntry(hash: AttoHash): AttoAccountEntry = throw UnsupportedOperationException()

    override fun accountEntryStream(): Flow<AttoAccountEntry> = emptyFlow()

    override fun accountEntryStream(hash: AttoHash): Flow<AttoAccountEntry> = emptyFlow()

    override fun accountEntryStream(
        address: AttoAddress,
        fromHeight: AttoHeight,
        toHeight: AttoHeight?,
    ): Flow<AttoAccountEntry> = emptyFlow()

    override fun accountEntryStream(search: HeightSearch): Flow<AttoAccountEntry> = emptyFlow()

    override suspend fun transaction(hash: AttoHash): AttoTransaction = throw UnsupportedOperationException()

    override fun transactionStream(): Flow<AttoTransaction> = emptyFlow()

    override fun transactionStream(hash: AttoHash): Flow<AttoTransaction> = emptyFlow()

    override fun transactionStream(
        address: AttoAddress,
        fromHeight: AttoHeight,
        toHeight: AttoHeight?,
    ): Flow<AttoTransaction> = emptyFlow()

    override fun transactionStream(search: HeightSearch): Flow<AttoTransaction> = emptyFlow()

    override suspend fun now(currentTime: AttoInstant): TimeDifferenceResponse =
        TimeDifferenceResponse(currentTime, WalletTestData.timestamp, 0)

    override suspend fun now(): AttoInstant = WalletTestData.timestamp

    override suspend fun publish(transaction: AttoTransaction) = Unit

    override suspend fun voterWeight(address: AttoAddress): AttoVoterWeight = throw UnsupportedOperationException()
}

internal object WalletTestData {
    val timestamp = AttoInstant.fromEpochMilliseconds(1_704_067_201_000L)
    val privateKey = AttoPrivateKey(ByteArray(32) { 1 })
    val address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32) { 1 }))
    val foreignAddress = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32) { 2 }))

    fun account(address: AttoAddress = this.address): AttoAccount =
        AttoAccount(
            address = address,
            network = AttoNetwork.LOCAL,
            version = 0U.toAttoVersion(),
            height = 1U.toAttoHeight(),
            balance = AttoAmount(100U),
            lastTransactionHash = AttoHash(ByteArray(32) { 3 }),
            lastTransactionTimestamp = timestamp,
            representativeAddress = foreignAddress,
        )

    fun entry(
        height: Int,
        address: AttoAddress = this.address,
    ): AttoAccountEntry =
        AttoAccountEntry(
            hash = AttoHash(ByteArray(32) { height.toByte() }),
            address = address,
            height = height.toAttoHeight(),
            blockType = AttoBlockType.RECEIVE,
            subjectAddress = foreignAddress,
            previousBalance = AttoAmount.MIN,
            balance = AttoAmount(1U),
            timestamp = timestamp,
        )

    fun transaction(
        height: Int,
        address: AttoAddress = this.address,
    ): AttoTransaction {
        val account = account(address).copy(height = (height - 1).toAttoHeight())
        val block = account.change(foreignAddress, timestamp).first
        return AttoTransaction(block, AttoSignature(ByteArray(64)), AttoWork(ByteArray(8)))
    }
}

internal class WalletTestWorker : AttoWorker {
    var closed = false

    override suspend fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoWork {
        val work = ByteArray(AttoWork.SIZE)
        var attempts = 0
        while (!AttoWork.isValid(threshold, target, work)) {
            for (index in work.indices) {
                work[index]++
                if (work[index] != 0.toByte()) break
            }
            if (++attempts % 4_096 == 0) yield()
        }
        return AttoWork(work)
    }

    override fun close() {
        closed = true
    }
}
