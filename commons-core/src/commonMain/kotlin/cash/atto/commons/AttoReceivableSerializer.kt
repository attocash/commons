package cash.atto.commons

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder

@OptIn(ExperimentalSerializationApi::class)
internal object AttoReceivableSerializer : KSerializer<AttoReceivable> {
    override val descriptor = AttoReceivableOutput.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoReceivable,
    ) {
        if (encoder is JsonEncoder) {
            val wire =
                AttoReceivableOutput(
                    receiverAddress = value.receiverAddress,
                    address = value.address,
                    network = value.network,
                    hash = value.hash,
                    version = value.version,
                    timestamp = value.timestamp,
                    amount = value.amount,
                )
            encoder.encodeSerializableValue(AttoReceivableOutput.serializer(), wire)
            return
        }

        val wire =
            AttoReceivableBinaryWire(
                network = value.network,
                hash = value.hash,
                version = value.version,
                algorithm = value.address.algorithm,
                publicKey = value.address.publicKey,
                timestamp = value.timestamp,
                receiverAlgorithm = value.receiverAddress.algorithm,
                receiverPublicKey = value.receiverAddress.publicKey,
                amount = value.amount,
            )
        encoder.encodeSerializableValue(AttoReceivableBinaryWire.serializer(), wire)
    }

    override fun deserialize(decoder: Decoder): AttoReceivable =
        if (decoder is JsonDecoder) {
            decoder.decodeSerializableValue(AttoReceivableOutput.serializer()).toReceivable()
        } else {
            decoder.decodeSerializableValue(AttoReceivableBinaryWire.serializer()).toReceivable()
        }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("cash.atto.commons.AttoReceivable")
private class AttoReceivableOutput(
    val network: AttoNetwork,
    val hash: AttoHash,
    val version: AttoVersion,
    val timestamp: AttoInstant,
    val amount: AttoAmount,
    val address: AttoAddress,
    val receiverAddress: AttoAddress,
) {
    fun toReceivable(): AttoReceivable =
        AttoReceivable(
            network = network,
            hash = hash,
            version = version,
            timestamp = timestamp,
            amount = amount,
            address = address,
            receiverAddress = receiverAddress,
        )
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("cash.atto.commons.AttoReceivableWire")
private class AttoReceivableBinaryWire(
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
) {
    fun toReceivable(): AttoReceivable =
        AttoReceivable(
            network = network,
            hash = hash,
            version = version,
            timestamp = timestamp,
            amount = amount,
            address = resolveSerializedAddress("address", address, algorithm, publicKey),
            receiverAddress = resolveSerializedAddress("receiverAddress", receiverAddress, receiverAlgorithm, receiverPublicKey),
        )
}
