package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoInstantAsStringSerializer
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoVoterWeight
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.time.Duration.Companion.milliseconds

interface AttoNodeOperations {
    companion object {}

    suspend fun account(publicKey: AttoPublicKey): AttoAccount?

    suspend fun account(addresses: Collection<AttoAddress>): Collection<AttoAccount>

    fun accountStream(publicKey: AttoPublicKey): Flow<AttoAccount>

    fun accountStream(addresses: Collection<AttoAddress>): Flow<AttoAccount>

    fun receivableStream(
        publicKey: AttoPublicKey,
        minAmount: AttoAmount = AttoAmount(1U),
    ): Flow<AttoReceivable>

    fun receivableStream(
        addresses: Collection<AttoAddress>,
        minAmount: AttoAmount = AttoAmount(1U),
    ): Flow<AttoReceivable>

    suspend fun accountEntry(hash: AttoHash): AttoAccountEntry

    fun accountEntryStream(
        publicKey: AttoPublicKey,
        fromHeight: AttoHeight = AttoHeight(1UL),
        toHeight: AttoHeight? = null,
    ): Flow<AttoAccountEntry>

    fun accountEntryStream(search: HeightSearch): Flow<AttoAccountEntry>

    suspend fun transaction(hash: AttoHash): AttoTransaction

    fun transactionStream(
        publicKey: AttoPublicKey,
        fromHeight: AttoHeight = AttoHeight(1UL),
        toHeight: AttoHeight? = null,
    ): Flow<AttoTransaction>

    fun transactionStream(search: HeightSearch): Flow<AttoTransaction>

    suspend fun now(currentTime: AttoInstant): TimeDifferenceResponse

    suspend fun now(): AttoInstant {
        val difference = now(AttoInstant.now()).differenceMillis
        return AttoInstant.now().plus(difference.milliseconds)
    }

    suspend fun publish(transaction: AttoTransaction)

    suspend fun voterWeight(address: AttoAddress): AttoVoterWeight
}

@OptIn(ExperimentalJsExport::class)
@Serializable
@JsExportForJs
data class TimeDifferenceResponse(
    @Serializable(AttoInstantAsStringSerializer::class)
    val clientInstant: AttoInstant,
    @Serializable(AttoInstantAsStringSerializer::class)
    val serverInstant: AttoInstant,
    val differenceMillis: Long,
)

@OptIn(ExperimentalJsExport::class)
@Serializable
@JsExportForJs
data class AccountSearch(
    val addresses: Collection<AttoAddress>,
)

@OptIn(ExperimentalJsExport::class)
@Serializable
@JsExportForJs
data class AccountHeightSearch(
    val address: AttoAddress,
    val fromHeight: AttoHeight,
    val toHeight: AttoHeight = AttoHeight.MAX,
)

@OptIn(ExperimentalJsExport::class)
@Serializable
@JsExportForJs
data class HeightSearch(
    val search: Collection<AccountHeightSearch>,
) {
    companion object {
        fun fromArray(search: Array<AccountHeightSearch>): HeightSearch = HeightSearch(search.toList())
    }
}
