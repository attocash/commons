package cash.atto.commons

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

@Serializable(with = AttoSignatureSerializer::class)
data class AttoSignature(
    val value: ByteArray,
) {
    companion object {
        const val SIZE = 64

        fun parse(value: String): AttoSignature {
            return AttoSignature(value.fromHexToByteArray())
        }

        fun sign(
            privateKey: AttoPrivateKey,
            hash: AttoHash,
        ): AttoSignature {
            val parameters = Ed25519PrivateKeyParameters(privateKey.value, 0)
            val signer = Ed25519Signer()
            signer.init(true, parameters)
            signer.update(hash.value, 0, hash.value.size)
            return AttoSignature(signer.generateSignature())
        }
    }

    init {
        value.checkLength(SIZE)
    }

    fun isValid(
        publicKey: AttoPublicKey,
        hash: AttoHash,
    ): Boolean {
        val parameters = Ed25519PublicKeyParameters(publicKey.value, 0)
        val signer = Ed25519Signer()
        signer.init(false, parameters)
        signer.update(hash.value, 0, hash.value.size)
        return signer.verifySignature(value)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttoSignature) return false

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = value.toHex()
}

fun AttoPrivateKey.sign(hash: AttoHash): AttoSignature = AttoSignature.sign(this, hash)

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
