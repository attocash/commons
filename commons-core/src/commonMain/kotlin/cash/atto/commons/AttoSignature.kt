package cash.atto.commons

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = AttoSignatureSerializer::class)
data class AttoSignature(
    val value: ByteArray,
) {
    companion object {
        const val SIZE = 64

        fun parse(value: String): AttoSignature {
            return AttoSignature(value.fromHexToByteArray())
        }
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

object AttoSignatureSerializer : KSerializer<AttoSignature> {
    override val descriptor = PrimitiveSerialDescriptor("AttoSignature", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoSignature,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoSignature = AttoSignature.parse(decoder.decodeString())
}
