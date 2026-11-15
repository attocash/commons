package cash.atto.commons

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder

@OptIn(ExperimentalSerializationApi::class)
internal object AttoAccountEntrySerializer : KSerializer<AttoAccountEntry> {
    override val descriptor = AttoAccountEntryOutput.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoAccountEntry,
    ) {
        if (encoder is JsonEncoder) {
            val wire =
                AttoAccountEntryOutput(
                    subjectAddress = value.subjectAddress,
                    address = value.address,
                    hash = value.hash,
                    height = value.height,
                    blockType = value.blockType,
                    previousBalance = value.previousBalance,
                    balance = value.balance,
                    timestamp = value.timestamp,
                )
            encoder.encodeSerializableValue(AttoAccountEntryOutput.serializer(), wire)
            return
        }

        val wire =
            AttoAccountEntryBinaryWire(
                hash = value.hash,
                algorithm = value.address.algorithm,
                publicKey = value.address.publicKey,
                height = value.height,
                blockType = value.blockType,
                subjectAlgorithm = value.subjectAddress.algorithm,
                subjectPublicKey = value.subjectAddress.publicKey,
                previousBalance = value.previousBalance,
                balance = value.balance,
                timestamp = value.timestamp,
            )
        encoder.encodeSerializableValue(AttoAccountEntryBinaryWire.serializer(), wire)
    }

    override fun deserialize(decoder: Decoder): AttoAccountEntry =
        if (decoder is JsonDecoder) {
            decoder.decodeSerializableValue(AttoAccountEntryOutput.serializer()).toAccountEntry()
        } else {
            decoder.decodeSerializableValue(AttoAccountEntryBinaryWire.serializer()).toAccountEntry()
        }
}

@Serializable
@SerialName("cash.atto.commons.AttoAccountEntry")
private class AttoAccountEntryOutput(
    val hash: AttoHash,
    val height: AttoHeight,
    val blockType: AttoBlockType,
    val previousBalance: AttoAmount,
    val balance: AttoAmount,
    val timestamp: AttoInstant,
    val address: AttoAddress,
    val subjectAddress: AttoAddress,
) {
    fun toAccountEntry(): AttoAccountEntry =
        AttoAccountEntry(
            hash = hash,
            height = height,
            blockType = blockType,
            previousBalance = previousBalance,
            balance = balance,
            timestamp = timestamp,
            address = address,
            subjectAddress = subjectAddress,
        )
}

@Serializable
@SerialName("cash.atto.commons.AttoAccountEntryWire")
private class AttoAccountEntryBinaryWire(
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
) {
    fun toAccountEntry(): AttoAccountEntry =
        AttoAccountEntry(
            hash = hash,
            height = height,
            blockType = blockType,
            previousBalance = previousBalance,
            balance = balance,
            timestamp = timestamp,
            address = resolveSerializedAddress("address", address, algorithm, publicKey),
            subjectAddress = resolveSerializedAddress("subjectAddress", subjectAddress, subjectAlgorithm, subjectPublicKey),
        )
}
