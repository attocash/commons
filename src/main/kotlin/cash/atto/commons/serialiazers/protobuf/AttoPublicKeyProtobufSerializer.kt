package cash.atto.commons.serialiazers.protobuf

import cash.atto.commons.AttoPublicKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoPublicKeyProtobufSerializer : KSerializer<AttoPublicKey> {
    override val descriptor = PrimitiveSerialDescriptor("AttoPublicKey", PrimitiveKind.BYTE)

    override fun serialize(encoder: Encoder, value: AttoPublicKey) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.value)
    }

    override fun deserialize(decoder: Decoder): AttoPublicKey {
        return AttoPublicKey(decoder.decodeSerializableValue(ByteArraySerializer()))
    }
}