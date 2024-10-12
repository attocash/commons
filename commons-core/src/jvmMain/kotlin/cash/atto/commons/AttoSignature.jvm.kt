package cash.atto.commons

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

actual fun AttoSignature.isValid(
    publicKey: AttoPublicKey,
    hash: AttoHash
): Boolean {
    val parameters = Ed25519PublicKeyParameters(publicKey.value, 0)
    val signer = Ed25519Signer()
    signer.init(false, parameters)
    signer.update(hash.value, 0, hash.value.size)
    return signer.verifySignature(value)
}
