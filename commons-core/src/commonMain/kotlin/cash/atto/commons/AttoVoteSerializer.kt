@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoVoteSerializer : KSerializer<AttoVote> {
    override val descriptor = SerialDescriptor("cash.atto.commons.AttoVote", AttoVoteWire.serializer().descriptor)

    override fun serialize(
        encoder: Encoder,
        value: AttoVote,
    ) {
        // Strict legacy readers require the original wire fields without address.
        encoder.encodeSerializableValue(
            AttoVoteWire.serializer(),
            AttoVoteWire(
                version = value.version,
                algorithm = value.algorithm,
                publicKey = value.publicKey,
                blockAlgorithm = value.blockAlgorithm,
                blockHash = value.blockHash,
                timestamp = value.timestamp,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): AttoVote =
        decodeWithSerializedProjections(
            decoder,
            AttoVoteWire.serializer(),
            mapOf("address" to listOf("algorithm", "publicKey")),
        ).toVote()
}

@Serializable
@SerialName("AttoVoteWire")
private data class AttoVoteWire(
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
