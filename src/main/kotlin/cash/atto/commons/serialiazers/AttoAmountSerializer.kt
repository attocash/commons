package cash.atto.commons.serialiazers

import cash.atto.commons.AttoAmount
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoAmountSerializer : KSerializer<AttoAmount> {
    override val descriptor = ULong.serializer().descriptor

    override fun serialize(encoder: Encoder, value: AttoAmount) {
        ULong.serializer().serialize(encoder, value.raw)
    }

    override fun deserialize(decoder: Decoder): AttoAmount {
        return AttoAmount(ULong.serializer().deserialize(decoder))
    }
}