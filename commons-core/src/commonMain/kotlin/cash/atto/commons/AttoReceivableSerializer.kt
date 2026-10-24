package cash.atto.commons

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalSerializationApi::class)
internal object AttoReceivableSerializer : KSerializer<AttoReceivable> {
    override val descriptor = SerialDescriptor("cash.atto.commons.AttoReceivable", AttoReceivableWire.serializer().descriptor)

    override fun serialize(
        encoder: Encoder,
        value: AttoReceivable,
    ) {
        val wire =
            AttoReceivableWire(
                network = value.network,
                hash = value.hash,
                version = value.version,
                algorithm = value.algorithm,
                publicKey = value.publicKey,
                timestamp = value.timestamp,
                receiverAlgorithm = value.receiverAlgorithm,
                receiverPublicKey = value.receiverPublicKey,
                amount = value.amount,
            )
        encoder.encodeSerializableValue(AttoReceivableWire.serializer(), wire)
    }

    override fun deserialize(decoder: Decoder): AttoReceivable {
        val wire =
            decodeWithSerializedProjections(
                decoder,
                AttoReceivableWire.serializer(),
                mapOf(
                    "address" to listOf("algorithm", "publicKey"),
                    "receiverAddress" to listOf("receiverAlgorithm", "receiverPublicKey"),
                ),
            )
        return AttoReceivable(
            network = wire.network,
            hash = wire.hash,
            version = wire.version,
            address = resolveSerializedAddress("address", wire.address, wire.algorithm, wire.publicKey),
            timestamp = wire.timestamp,
            receiverAddress =
                resolveSerializedAddress(
                    "receiverAddress",
                    wire.receiverAddress,
                    wire.receiverAlgorithm,
                    wire.receiverPublicKey,
                ),
            amount = wire.amount,
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private class AttoReceivableWire(
    val network: AttoNetwork,
    val hash: AttoHash,
    val version: AttoVersion,
    val algorithm: AttoAlgorithm? = null,
    val publicKey: AttoPublicKey? = null,
    val timestamp: AttoInstant,
    val receiverAlgorithm: AttoAlgorithm? = null,
    val receiverPublicKey: AttoPublicKey? = null,
    val amount: AttoAmount,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val address: AttoAddress? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val receiverAddress: AttoAddress? = null,
)
