@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cash.atto.commons

import cash.atto.commons.utils.CryptoKey
import cash.atto.commons.utils.getSubtleCryptoInstance
import cash.atto.commons.utils.mapKeyUsages
import org.khronos.webgl.Uint8Array

internal object InMemorySignerHolder

internal actual class Ed25519SigningKey(
    private val cryptoKey: CryptoKey,
    actual val publicKey: AttoPublicKey,
) {
    actual suspend fun sign(hash: AttoHash): AttoSignature {
        val signature =
            getSubtleCryptoInstance()
                .sign(
                    algorithm = ed25519Algorithm(),
                    key = cryptoKey,
                    data = hash.value.toUint8Array(),
                ).await()

        return AttoSignature(Uint8Array(signature).toByteArray())
    }
}

internal actual suspend fun loadEd25519SigningKey(privateKey: AttoPrivateKey): Ed25519SigningKey {
    val crypto = getSubtleCryptoInstance()
    val cryptoKey =
        crypto
            .importKey(
                format = "pkcs8",
                keyData = privateKey.toEd25519Pkcs8().toUint8Array(),
                algorithm = ed25519Algorithm(),
                extractable = true,
                keyUsages = mapKeyUsages("sign"),
            ).await()
    val publicKey =
        AttoPublicKey(
            crypto
                .exportKey("jwk", cryptoKey)
                .await()
                .x
                .toString()
                .base64UrlToByteArray(),
        )

    return Ed25519SigningKey(cryptoKey, publicKey)
}

private fun ed25519Algorithm(): JsAny = js("""({ "name": "Ed25519" })""")
