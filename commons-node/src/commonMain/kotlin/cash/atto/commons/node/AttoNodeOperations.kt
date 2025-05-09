package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

interface AttoNodeOperations {
    companion object {}

    val network: AttoNetwork

    suspend fun account(publicKey: AttoPublicKey): AttoAccount?

    suspend fun account(addresses: List<AttoAddress>): Collection<AttoAccount>

    fun accountStream(publicKey: AttoPublicKey): Flow<AttoAccount>

    fun accountStream(addresses: Collection<AttoAddress>): Flow<AttoAccount>

    fun receivableStream(publicKey: AttoPublicKey): Flow<AttoReceivable>

    fun receivableStream(addresses: List<AttoAddress>): Flow<AttoReceivable>

    fun accountEntryStream(
        publicKey: AttoPublicKey,
        fromHeight: AttoHeight = AttoHeight(1UL),
        toHeight: AttoHeight? = null,
    ): Flow<AttoAccountEntry>

    fun accountEntryStream(search: HeightSearch): Flow<AttoAccountEntry>

    fun transactionStream(
        publicKey: AttoPublicKey,
        fromHeight: AttoHeight = AttoHeight(1UL),
        toHeight: AttoHeight? = null,
    ): Flow<AttoTransaction>

    fun transactionStream(search: HeightSearch): Flow<AttoTransaction>

    suspend fun now(currentTime: Instant): TimeDifferenceResponse

    suspend fun now(): Instant {
        val difference = now(Clock.System.now()).differenceMillis
        return Clock.System.now().plus(difference.milliseconds)
    }

    suspend fun publish(transaction: AttoTransaction)

    @Serializable
    data class TimeDifferenceResponse(
        val clientInstant: Instant,
        val serverInstant: Instant,
        val differenceMillis: Long,
    )

    @Serializable
    data class AccountSearch(
        val addresses: Collection<String>,
    )

    @Serializable
    data class AccountHeightSearch(
        val address: String,
        val fromHeight: ULong,
        val toHeight: ULong = ULong.MAX_VALUE,
    )

    @Serializable
    data class HeightSearch(
        val search: Collection<AccountHeightSearch>,
    )
}
