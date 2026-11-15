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
internal object AttoAccountSerializer : KSerializer<AttoAccount> {
    override val descriptor = AttoAccountOutput.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoAccount,
    ) {
        if (encoder is JsonEncoder) {
            val wire =
                AttoAccountOutput(
                    representativeAddress = value.representativeAddress,
                    address = value.address,
                    network = value.network,
                    version = value.version,
                    height = value.height,
                    balance = value.balance,
                    lastTransactionHash = value.lastTransactionHash,
                    lastTransactionTimestamp = value.lastTransactionTimestamp,
                )
            encoder.encodeSerializableValue(AttoAccountOutput.serializer(), wire)
            return
        }

        val wire =
            AttoAccountBinaryWire(
                publicKey = value.address.publicKey,
                network = value.network,
                version = value.version,
                algorithm = value.address.algorithm,
                height = value.height,
                balance = value.balance,
                lastTransactionHash = value.lastTransactionHash,
                lastTransactionTimestamp = value.lastTransactionTimestamp,
                representativeAlgorithm = value.representativeAddress.algorithm,
                representativePublicKey = value.representativeAddress.publicKey,
            )
        encoder.encodeSerializableValue(AttoAccountBinaryWire.serializer(), wire)
    }

    override fun deserialize(decoder: Decoder): AttoAccount =
        if (decoder is JsonDecoder) {
            decoder.decodeSerializableValue(AttoAccountOutput.serializer()).toAccount()
        } else {
            decoder.decodeSerializableValue(AttoAccountBinaryWire.serializer()).toAccount()
        }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("cash.atto.commons.AttoAccount")
private class AttoAccountOutput(
    val network: AttoNetwork,
    val version: AttoVersion,
    val height: AttoHeight,
    val balance: AttoAmount,
    val lastTransactionHash: AttoHash,
    val lastTransactionTimestamp: AttoInstant,
    val address: AttoAddress,
    val representativeAddress: AttoAddress,
) {
    fun toAccount(): AttoAccount =
        AttoAccount(
            network = network,
            version = version,
            height = height,
            balance = balance,
            lastTransactionHash = lastTransactionHash,
            lastTransactionTimestamp = lastTransactionTimestamp,
            address = address,
            representativeAddress =
            representativeAddress,
        )
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("cash.atto.commons.AttoAccountWire")
private class AttoAccountBinaryWire(
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
) {
    fun toAccount(): AttoAccount =
        AttoAccount(
            network = network,
            version = version,
            height = height,
            balance = balance,
            lastTransactionHash = lastTransactionHash,
            lastTransactionTimestamp = lastTransactionTimestamp,
            address = resolveSerializedAddress("address", address, algorithm, publicKey),
            representativeAddress =
                resolveSerializedAddress(
                    "representativeAddress",
                    representativeAddress,
                    representativeAlgorithm,
                    representativePublicKey,
                ),
        )
}
