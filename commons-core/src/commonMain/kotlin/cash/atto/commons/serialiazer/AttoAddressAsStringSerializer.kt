package cash.atto.commons.serialiazer

import cash.atto.commons.AttoAddress
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoAddressAsStringSerializer : KSerializer<AttoAddress> {
    override val descriptor = PrimitiveSerialDescriptor("AttoAddress", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoAddress,
    ) {
        encoder.encodeString(value.path)
    }

    override fun deserialize(decoder: Decoder): AttoAddress = AttoAddress.parsePath(decoder.decodeString())
}
