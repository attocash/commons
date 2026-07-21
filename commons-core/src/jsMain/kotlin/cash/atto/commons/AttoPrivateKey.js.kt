package cash.atto.commons

import cash.atto.commons.utils.getSubtleCryptoInstance
import org.khronos.webgl.Uint8Array
import kotlin.js.json

internal object AttoPrivateKeyHolder

internal actual suspend fun hmacSha512(
    secretKey: ByteArray,
    data: ByteArray,
): ByteArray {
    val crypto = getSubtleCryptoInstance()
    val key =
        crypto
            .importKey(
                format = "raw",
                keyData = secretKey.toUint8Array(),
                algorithm = json("name" to "HMAC", "hash" to "SHA-512"),
                extractable = false,
                keyUsages = arrayOf("sign"),
            ).await()
    val digest =
        crypto
            .sign(
                algorithm = json("name" to "HMAC"),
                key = key,
                data = data.toUint8Array(),
            ).await()

    return Uint8Array(digest).toByteArray()
}
