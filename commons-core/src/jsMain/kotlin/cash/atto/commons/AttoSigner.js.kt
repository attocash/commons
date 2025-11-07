package cash.atto.commons

import cash.atto.commons.utils.generateKeyPairFromSeed

internal object InMemorySignerHolder

actual class InMemorySigner actual constructor(
    internal actual val privateKey: AttoPrivateKey,
) : AttoSigner {
    actual override val algorithm: AttoAlgorithm = AttoAlgorithm.V1
    actual override val publicKey: AttoPublicKey = privateKey.toPublicKey()
    actual override val address: AttoAddress = publicKey.toAddress(AttoAlgorithm.V1)

    private val keyPair = generateKeyPairFromSeed(privateKey.value.toUint8Array())

    actual override suspend fun sign(hash: AttoHash): AttoSignature {
        val signature =
            cash.atto.commons.utils.sign(
                secretKey = keyPair.secretKey,
                message = hash.value.toUint8Array(),
            )

        return AttoSignature(signature.toByteArray())
    }
}
