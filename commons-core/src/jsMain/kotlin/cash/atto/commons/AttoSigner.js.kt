package cash.atto.commons

import cash.atto.commons.utils.generateKeyPairFromSeed

actual class InMemorySigner actual constructor(internal actual val privateKey: AttoPrivateKey) : AttoSigner {
    actual override val publicKey: AttoPublicKey = privateKey.toPublicKey()

    private val keyPair = generateKeyPairFromSeed(privateKey.value.toUint8Array())


    actual override suspend fun sign(hash: AttoHash): AttoSignature {
        val signature = cash.atto.commons.utils.sign(
            secretKey = keyPair.secretKey,
            message = hash.value.toUint8Array()
        )

        return AttoSignature(signature.toByteArray())
    }
}
