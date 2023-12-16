package cash.atto.commons

import cash.atto.commons.serialiazers.AttoSignatureSerializer
import kotlinx.serialization.Serializable
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

@Serializable(with = AttoSignatureSerializer::class)
data class AttoSignature(val value: ByteArray) {
    companion object {
        const val SIZE = 64
        fun parse(value: String): AttoSignature {
            return AttoSignature(value.fromHexToByteArray())
        }

        fun sign(privateKey: AttoPrivateKey, hash: AttoHash): AttoSignature {
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

    fun isValid(publicKey: AttoPublicKey, hash: AttoHash): Boolean {
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

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    override fun toString(): String {
        return value.toHex()
    }
}

fun AttoPrivateKey.sign(hash: AttoHash): AttoSignature {
    return AttoSignature.sign(this, hash)
}