package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.ExperimentalJsExport

@OptIn(ExperimentalJsExport::class)
@Serializable(with = AttoPublicKeyAsStringSerializer::class)
@JsExportForJs
data class AttoPublicKey(
    val value: ByteArray,
) {
    init {
        value.checkLength(32)
    }

    companion object {
        fun parse(value: String): AttoPublicKey {
            return AttoPublicKey(value.fromHexToByteArray())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttoPublicKey) return false

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = value.toHex()
}

expect fun AttoPrivateKey.toPublicKey(): AttoPublicKey

object AttoPublicKeyAsStringSerializer : KSerializer<AttoPublicKey> {
    override val descriptor = PrimitiveSerialDescriptor("AttoPublicKey", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoPublicKey,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoPublicKey = AttoPublicKey.parse(decoder.decodeString())
}

object AttoPublicKeyAsByteArraySerializer : KSerializer<AttoPublicKey> {
    override val descriptor: SerialDescriptor = ByteArraySerializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoPublicKey,
    ) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.value)
    }

    override fun deserialize(decoder: Decoder): AttoPublicKey = AttoPublicKey(decoder.decodeSerializableValue(ByteArraySerializer()))
}
