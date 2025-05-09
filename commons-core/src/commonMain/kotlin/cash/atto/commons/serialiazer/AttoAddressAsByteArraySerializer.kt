package cash.atto.commons.serialiazer

import cash.atto.commons.AttoAddress
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoAddressAsByteArraySerializer : KSerializer<AttoAddress> {
    override val descriptor = PrimitiveSerialDescriptor("AttoAddress", PrimitiveKind.BYTE)

    override fun serialize(
        encoder: Encoder,
        value: AttoAddress,
    ) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.toBuffer().readByteArray())
    }

    override fun deserialize(decoder: Decoder): AttoAddress = AttoAddress.parse(decoder.decodeSerializableValue(ByteArraySerializer()))
}
