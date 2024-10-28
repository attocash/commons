package cash.atto.commons.serialiazer

import cash.atto.commons.AttoWork
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoWorkAsByteArraySerializer : KSerializer<AttoWork> {
    override val descriptor = PrimitiveSerialDescriptor("AttoWork", PrimitiveKind.BYTE)

    override fun serialize(
        encoder: Encoder,
        value: AttoWork,
    ) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.value)
    }

    override fun deserialize(decoder: Decoder): AttoWork = AttoWork(decoder.decodeSerializableValue(ByteArraySerializer()))
}
