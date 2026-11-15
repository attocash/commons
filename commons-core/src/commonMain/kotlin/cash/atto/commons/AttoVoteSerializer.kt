@file:OptIn(ExperimentalSerializationApi::class)

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

object AttoVoteSerializer : KSerializer<AttoVote> {
    override val descriptor = AttoVoteOutput.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoVote,
    ) {
        if (encoder is JsonEncoder) {
            encoder.encodeSerializableValue(
                AttoVoteOutput.serializer(),
                AttoVoteOutput(
                    address = value.address,
                    version = value.version,
                    blockAlgorithm = value.blockAlgorithm,
                    blockHash = value.blockHash,
                    timestamp = value.timestamp,
                ),
            )
            return
        }

        // Strict legacy readers require the original wire fields without address.
        encoder.encodeSerializableValue(
            AttoVoteBinaryWire.serializer(),
            AttoVoteBinaryWire(
                version = value.version,
                algorithm = value.address.algorithm,
                publicKey = value.address.publicKey,
                blockAlgorithm = value.blockAlgorithm,
                blockHash = value.blockHash,
                timestamp = value.timestamp,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): AttoVote =
        if (decoder is JsonDecoder) {
            decoder.decodeSerializableValue(AttoVoteOutput.serializer()).toVote()
        } else {
            decoder.decodeSerializableValue(AttoVoteBinaryWire.serializer()).toVote()
        }
}

@Serializable
@SerialName("cash.atto.commons.AttoVote")
private class AttoVoteOutput(
    val version: AttoVersion,
    val blockAlgorithm: AttoAlgorithm,
    val blockHash: AttoHash,
    val timestamp: AttoInstant,
    val address: AttoAddress,
) {
    fun toVote(): AttoVote =
        AttoVote(
            version = version,
            address = address,
            blockAlgorithm = blockAlgorithm,
            blockHash = blockHash,
            timestamp = timestamp,
        )
}

@Serializable
@SerialName("AttoVoteWire")
private data class AttoVoteBinaryWire(
    val version: AttoVersion,
    val algorithm: AttoAlgorithm? = null,
    val publicKey: AttoPublicKey? = null,
    val blockAlgorithm: AttoAlgorithm,
    val blockHash: AttoHash,
    val timestamp: AttoInstant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val address: AttoAddress? = null,
) {
    fun toVote(): AttoVote =
        AttoVote(
            version = version,
            address = resolveSerializedAddress("address", address, algorithm, publicKey),
            blockAlgorithm = blockAlgorithm,
            blockHash = blockHash,
            timestamp = timestamp,
        )
}
