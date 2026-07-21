package cash.atto.commons

import cash.atto.commons.utils.getSubtleCryptoInstance
import kotlin.js.json

internal actual suspend fun verifyEd25519(
    signature: AttoSignature,
    publicKey: AttoPublicKey,
    hash: AttoHash,
): Boolean {
    val crypto = getSubtleCryptoInstance()
    val cryptoKey =
        crypto
            .importKey(
                format = "raw",
                keyData = publicKey.value.toUint8Array(),
                algorithm = json("name" to "Ed25519"),
                extractable = false,
                keyUsages = arrayOf("verify"),
            ).await()

    return crypto
        .verify(
            algorithm = json("name" to "Ed25519"),
            key = cryptoKey,
            signature = signature.value.toUint8Array(),
            data = hash.value.toUint8Array(),
        ).await()
}
