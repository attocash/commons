package cash.atto.commons

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalSerializationApi::class)
internal object AttoAccountEntrySerializer : KSerializer<AttoAccountEntry> {
    override val descriptor = SerialDescriptor("cash.atto.commons.AttoAccountEntry", AttoAccountEntryWire.serializer().descriptor)

    override fun serialize(
        encoder: Encoder,
        value: AttoAccountEntry,
    ) {
        val wire =
            AttoAccountEntryWire(
                hash = value.hash,
                algorithm = value.algorithm,
                publicKey = value.publicKey,
                height = value.height,
                blockType = value.blockType,
                subjectAlgorithm = value.subjectAlgorithm,
                subjectPublicKey = value.subjectPublicKey,
                previousBalance = value.previousBalance,
                balance = value.balance,
                timestamp = value.timestamp,
            )
        encoder.encodeSerializableValue(AttoAccountEntryWire.serializer(), wire)
    }

    override fun deserialize(decoder: Decoder): AttoAccountEntry {
        val wire =
            decodeWithSerializedProjections(
                decoder,
                AttoAccountEntryWire.serializer(),
                mapOf(
                    "address" to listOf("algorithm", "publicKey"),
                    "subjectAddress" to listOf("subjectAlgorithm", "subjectPublicKey"),
                ),
            )
        return AttoAccountEntry(
            hash = wire.hash,
            address = resolveSerializedAddress("address", wire.address, wire.algorithm, wire.publicKey),
            height = wire.height,
            blockType = wire.blockType,
            subjectAddress = resolveSerializedAddress("subjectAddress", wire.subjectAddress, wire.subjectAlgorithm, wire.subjectPublicKey),
            previousBalance = wire.previousBalance,
            balance = wire.balance,
            timestamp = wire.timestamp,
        )
    }
}

@Serializable
private class AttoAccountEntryWire(
    val hash: AttoHash,
    val algorithm: AttoAlgorithm? = null,
    val publicKey: AttoPublicKey? = null,
    val height: AttoHeight,
    val blockType: AttoBlockType,
    val subjectAlgorithm: AttoAlgorithm? = null,
    val subjectPublicKey: AttoPublicKey? = null,
    val previousBalance: AttoAmount,
    val balance: AttoAmount,
    val timestamp: AttoInstant,
    val address: AttoAddress? = if (algorithm != null && publicKey != null) AttoAddress(algorithm, publicKey) else null,
    val subjectAddress: AttoAddress? =
        if (subjectAlgorithm != null && subjectPublicKey != null) AttoAddress(subjectAlgorithm, subjectPublicKey) else null,
)
