package cash.atto.commons

import cash.atto.commons.utils.CryptoKey
import cash.atto.commons.utils.getSubtleCryptoInstance
import org.khronos.webgl.Uint8Array
import kotlin.js.json

internal object InMemorySignerHolder

internal actual class Ed25519SigningKey(
    private val cryptoKey: CryptoKey,
    actual val publicKey: AttoPublicKey,
) {
    actual suspend fun sign(hash: AttoHash): AttoSignature {
        val signature =
            getSubtleCryptoInstance()
                .sign(
                    algorithm = json("name" to "Ed25519"),
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
                algorithm = json("name" to "Ed25519"),
                extractable = true,
                keyUsages = arrayOf("sign"),
            ).await()
    val publicKey =
        AttoPublicKey(
            crypto
                .exportKey("jwk", cryptoKey)
                .await()
                .x
                .base64UrlToByteArray(),
        )

    return Ed25519SigningKey(cryptoKey, publicKey)
}
