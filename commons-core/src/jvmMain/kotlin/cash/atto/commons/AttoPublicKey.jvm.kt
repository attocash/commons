@file:JvmName("AttoPublicKeys")

package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

@JsExportForJs
actual fun AttoPrivateKey.toPublicKey(): AttoPublicKey {
    val publicKey =
        Ed25519PrivateKeyParameters(
            this.value,
            0,
        ).generatePublicKey().encoded

    return AttoPublicKey(publicKey)
}
