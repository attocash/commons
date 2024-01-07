package cash.atto.commons.serialiazers.json

import cash.atto.commons.AttoPublicKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoPublicKeyJsonSerializer : KSerializer<AttoPublicKey> {
    override val descriptor = PrimitiveSerialDescriptor("AttoPublicKey", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AttoPublicKey) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoPublicKey {
        return AttoPublicKey.parse(decoder.decodeString())
    }
}