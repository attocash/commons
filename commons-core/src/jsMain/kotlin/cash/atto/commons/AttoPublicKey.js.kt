package cash.atto.commons

import cash.atto.commons.utils.generateKeyPairFromSeed

actual fun AttoPrivateKey.toPublicKey(): AttoPublicKey {
    val pair = generateKeyPairFromSeed(this.value.toUint8Array())

    return AttoPublicKey(pair.publicKey.toByteArray())
}
