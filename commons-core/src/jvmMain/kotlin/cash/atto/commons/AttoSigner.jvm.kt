package cash.atto.commons

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

actual class InMemorySigner actual constructor(
    internal actual val privateKey: AttoPrivateKey
) : AttoSigner {
    actual override val publicKey: AttoPublicKey = privateKey.toPublicKey()

    actual override suspend fun sign(hash: AttoHash): AttoSignature {
        val parameters = Ed25519PrivateKeyParameters(privateKey.value, 0)
        val signer = Ed25519Signer()
        signer.init(true, parameters)
        signer.update(hash.value, 0, hash.value.size)
        return AttoSignature(signer.generateSignature())
    }
}
