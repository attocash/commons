package cash.atto.commons.serialiazers.protobuf

import cash.atto.commons.AttoSignature
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoSignatureProtobufSerializer : KSerializer<AttoSignature> {
    override val descriptor = PrimitiveSerialDescriptor("AttoSignature", PrimitiveKind.BYTE)

    override fun serialize(encoder: Encoder, value: AttoSignature) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.value)
    }

    override fun deserialize(decoder: Decoder): AttoSignature {
        return AttoSignature(decoder.decodeSerializableValue(ByteArraySerializer()))
    }
}