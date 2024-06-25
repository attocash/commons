package cash.atto.commons.serialiazers.json

import cash.atto.commons.AttoSignature
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoSignatureJsonSerializer : KSerializer<AttoSignature> {
    override val descriptor = PrimitiveSerialDescriptor("AttoSignature", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoSignature,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoSignature = AttoSignature.parse(decoder.decodeString())
}
