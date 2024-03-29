package cash.atto.commons.serialiazers.protobuf

import cash.atto.commons.AttoHash
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoHashProtobufSerializer : KSerializer<AttoHash> {
    override val descriptor = PrimitiveSerialDescriptor("AttoHash", PrimitiveKind.BYTE)

    override fun serialize(encoder: Encoder, value: AttoHash) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.value)
    }

    override fun deserialize(decoder: Decoder): AttoHash {
        return AttoHash(decoder.decodeSerializableValue(ByteArraySerializer()))
    }
}