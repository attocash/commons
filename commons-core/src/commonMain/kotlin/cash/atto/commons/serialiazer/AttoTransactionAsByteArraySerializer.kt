package cash.atto.commons.serialiazer

import cash.atto.commons.AttoTransaction
import cash.atto.commons.toBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoTransactionAsByteArraySerializer : KSerializer<AttoTransaction> {
    override val descriptor = PrimitiveSerialDescriptor("AttoTransaction", PrimitiveKind.BYTE)

    override fun serialize(
        encoder: Encoder,
        value: AttoTransaction,
    ) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.toBuffer().readByteArray())
    }

    override fun deserialize(decoder: Decoder): AttoTransaction =
        AttoTransaction.fromBuffer(decoder.decodeSerializableValue(ByteArraySerializer()).toBuffer())!!
}
