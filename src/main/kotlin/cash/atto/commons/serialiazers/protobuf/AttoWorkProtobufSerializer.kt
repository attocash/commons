package cash.atto.commons.serialiazers.protobuf

import cash.atto.commons.AttoWork
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoWorkProtobufSerializer : KSerializer<AttoWork> {
    override val descriptor = PrimitiveSerialDescriptor("AttoWork", PrimitiveKind.BYTE)

    override fun serialize(encoder: Encoder, value: AttoWork) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.value)
    }

    override fun deserialize(decoder: Decoder): AttoWork {
        return AttoWork(decoder.decodeSerializableValue(ByteArraySerializer()))
    }
}