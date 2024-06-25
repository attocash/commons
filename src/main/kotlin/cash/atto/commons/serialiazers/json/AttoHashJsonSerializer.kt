package cash.atto.commons.serialiazers.json

import cash.atto.commons.AttoHash
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoHashJsonSerializer : KSerializer<AttoHash> {
    override val descriptor = PrimitiveSerialDescriptor("AttoHash", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoHash,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoHash = AttoHash.parse(decoder.decodeString())
}
