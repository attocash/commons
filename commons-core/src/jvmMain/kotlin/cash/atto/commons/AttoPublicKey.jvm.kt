package cash.atto.commons

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

actual fun AttoPrivateKey.toPublicKey(): AttoPublicKey {
    val publicKey = Ed25519PrivateKeyParameters(
        this.value,
        0,
    ).generatePublicKey().encoded

    return AttoPublicKey(publicKey)
}
