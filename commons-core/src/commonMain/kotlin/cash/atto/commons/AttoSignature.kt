package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.ExperimentalJsExport

@Serializable(with = AttoSignatureAsStringSerializer::class)
@OptIn(ExperimentalJsExport::class)
@JsExportForJs
data class AttoSignature(
    val value: ByteArray,
) {
    companion object {
        const val SIZE = 64

        fun parse(value: String): AttoSignature = AttoSignature(value.fromHexToByteArray())
    }

    init {
        value.checkLength(SIZE)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttoSignature) return false

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = value.toHex()
}

expect fun AttoSignature.isValid(
    publicKey: AttoPublicKey,
    hash: AttoHash,
): Boolean

fun AttoSignature.isValid(
    publicKey: AttoPublicKey,
    challenge: AttoChallenge,
): Boolean {
    val hash = AttoHash.hash(64, publicKey.value, challenge.value)
    return isValid(publicKey, challenge)
}

object AttoSignatureAsStringSerializer : KSerializer<AttoSignature> {
    override val descriptor = PrimitiveSerialDescriptor("AttoSignatureAsString", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoSignature,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoSignature = AttoSignature.parse(decoder.decodeString())
}

object AttoSignatureAsByteArraySerializer : KSerializer<AttoSignature> {
    override val descriptor = PrimitiveSerialDescriptor("AttoSignatureAsByteArray", PrimitiveKind.BYTE)

    override fun serialize(
        encoder: Encoder,
        value: AttoSignature,
    ) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.value)
    }

    override fun deserialize(decoder: Decoder): AttoSignature = AttoSignature(decoder.decodeSerializableValue(ByteArraySerializer()))
}
