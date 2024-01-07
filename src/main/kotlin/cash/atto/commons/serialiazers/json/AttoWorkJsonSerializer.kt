package cash.atto.commons.serialiazers.json

import cash.atto.commons.AttoWork
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoWorkJsonSerializer : KSerializer<AttoWork> {
    override val descriptor = PrimitiveSerialDescriptor("AttoWork", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AttoWork) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoWork {
        return AttoWork.parse(decoder.decodeString())
    }
}