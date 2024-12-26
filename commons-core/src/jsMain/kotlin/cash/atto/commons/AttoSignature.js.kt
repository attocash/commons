package cash.atto.commons

import cash.atto.commons.utils.verify

actual fun AttoSignature.isValid(
    publicKey: AttoPublicKey,
    hash: AttoHash
): Boolean {

    val publicKeyUint8 = publicKey.value.toUint8Array()

    val signatureUint8 = this.value.toUint8Array()

    val hashUint8 = hash.value.toUint8Array()

    return verify(
        publicKey = publicKeyUint8,
        message = hashUint8,
        signature = signatureUint8
    )
}
