package cash.atto.commons

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalSerializationApi::class)
internal object AttoAccountSerializer : KSerializer<AttoAccount> {
    override val descriptor = SerialDescriptor("cash.atto.commons.AttoAccount", AttoAccountWire.serializer().descriptor)

    override fun serialize(
        encoder: Encoder,
        value: AttoAccount,
    ) {
        val wire =
            AttoAccountWire(
                publicKey = value.publicKey,
                network = value.network,
                version = value.version,
                algorithm = value.algorithm,
                height = value.height,
                balance = value.balance,
                lastTransactionHash = value.lastTransactionHash,
                lastTransactionTimestamp = value.lastTransactionTimestamp,
                representativeAlgorithm = value.representativeAlgorithm,
                representativePublicKey = value.representativePublicKey,
            )
        encoder.encodeSerializableValue(AttoAccountWire.serializer(), wire)
    }

    override fun deserialize(decoder: Decoder): AttoAccount {
        val wire =
            decodeWithSerializedProjections(
                decoder,
                AttoAccountWire.serializer(),
                mapOf(
                    "address" to listOf("algorithm", "publicKey"),
                    "representativeAddress" to listOf("representativeAlgorithm", "representativePublicKey"),
                ),
            )
        return AttoAccount(
            address = resolveSerializedAddress("address", wire.address, wire.algorithm, wire.publicKey),
            network = wire.network,
            version = wire.version,
            height = wire.height,
            balance = wire.balance,
            lastTransactionHash = wire.lastTransactionHash,
            lastTransactionTimestamp = wire.lastTransactionTimestamp,
            representativeAddress =
                resolveSerializedAddress(
                    "representativeAddress",
                    wire.representativeAddress,
                    wire.representativeAlgorithm,
                    wire.representativePublicKey,
                ),
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private class AttoAccountWire(
    val publicKey: AttoPublicKey? = null,
    val network: AttoNetwork,
    val version: AttoVersion,
    val algorithm: AttoAlgorithm? = null,
    val height: AttoHeight,
    val balance: AttoAmount,
    val lastTransactionHash: AttoHash,
    val lastTransactionTimestamp: AttoInstant,
    val representativeAlgorithm: AttoAlgorithm? = null,
    val representativePublicKey: AttoPublicKey? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val address: AttoAddress? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val representativeAddress: AttoAddress? = null,
)
