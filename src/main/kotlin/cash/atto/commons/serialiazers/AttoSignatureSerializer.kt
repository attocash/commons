package cash.atto.commons.serialiazers

import cash.atto.commons.AttoSignature
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoSignatureSerializer : KSerializer<AttoSignature> {
    override val descriptor = PrimitiveSerialDescriptor("AttoSignature", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AttoSignature) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoSignature {
        return AttoSignature.parse(decoder.decodeString())
    }
}