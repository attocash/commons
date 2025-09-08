package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.ExperimentalJsExport
import kotlin.time.Duration.Companion.milliseconds

interface AttoNodeOperations {
    companion object {}

    val network: AttoNetwork

    suspend fun account(publicKey: AttoPublicKey): AttoAccount?

    suspend fun account(addresses: Collection<AttoAddress>): Collection<AttoAccount>

    fun accountStream(publicKey: AttoPublicKey): Flow<AttoAccount>

    fun accountStream(addresses: Collection<AttoAddress>): Flow<AttoAccount>

    fun receivableStream(publicKey: AttoPublicKey): Flow<AttoReceivable>

    fun receivableStream(addresses: Collection<AttoAddress>): Flow<AttoReceivable>

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

    suspend fun now(currentTime: Instant): TimeDifferenceResponse

    suspend fun now(): Instant {
        val difference = now(Clock.System.now()).differenceMillis
        return Clock.System.now().plus(difference.milliseconds)
    }

    suspend fun publish(transaction: AttoTransaction)
}

@OptIn(ExperimentalJsExport::class)
@Serializable
@JsExportForJs
data class TimeDifferenceResponse(
    val clientInstant: Instant,
    val serverInstant: Instant,
    val differenceMillis: Long,
)

@OptIn(ExperimentalJsExport::class)
@Serializable
@JsExportForJs
data class AccountSearch(
    val addresses: Collection<
        @Serializable(with = AttoAddressSerializer::class)
        AttoAddress,
        >,
)

@OptIn(ExperimentalJsExport::class)
@Serializable
@JsExportForJs
data class AccountHeightSearch(
    @Serializable(with = AttoAddressSerializer::class)
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

object AttoAddressSerializer : KSerializer<AttoAddress> {
    override val descriptor = PrimitiveSerialDescriptor("AttoAddress", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoAddress,
    ) {
        encoder.encodeString(value.path)
    }

    override fun deserialize(decoder: Decoder): AttoAddress {
        val address = decoder.decodeString()
        if (address.startsWith("atto://")) {
            return AttoAddress.parse(address)
        }
        return AttoAddress.parsePath(address)
    }
}
