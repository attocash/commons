@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cash.atto.commons

import cash.atto.commons.utils.getSubtleCryptoInstance
import cash.atto.commons.utils.mapKeyUsages

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
                algorithm = ed25519Algorithm(),
                extractable = false,
                keyUsages = mapKeyUsages("verify"),
            ).await()

    return crypto
        .verify(
            algorithm = ed25519Algorithm(),
            key = cryptoKey,
            signature = signature.value.toUint8Array(),
            data = hash.value.toUint8Array(),
        ).await()
        .toBoolean()
}

private fun ed25519Algorithm(): JsAny = js("""({ "name": "Ed25519" })""")
