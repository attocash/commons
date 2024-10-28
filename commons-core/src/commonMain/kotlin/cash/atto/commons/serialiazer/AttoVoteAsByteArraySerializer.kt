package cash.atto.commons.serialiazer

import cash.atto.commons.AttoSignedVote
import cash.atto.commons.fromBuffer
import cash.atto.commons.toBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoSignedVoteAsByteArraySerializer : KSerializer<AttoSignedVote> {
    override val descriptor = PrimitiveSerialDescriptor("AttoSignedVote", PrimitiveKind.BYTE)

    override fun serialize(
        encoder: Encoder,
        value: AttoSignedVote,
    ) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.toBuffer().readByteArray())
    }

    override fun deserialize(decoder: Decoder): AttoSignedVote =
        AttoSignedVote.fromBuffer(decoder.decodeSerializableValue(ByteArraySerializer()).toBuffer())
}
