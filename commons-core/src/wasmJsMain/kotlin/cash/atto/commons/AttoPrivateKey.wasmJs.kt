@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cash.atto.commons

import cash.atto.commons.utils.getSubtleCryptoInstance
import cash.atto.commons.utils.mapKeyUsages
import org.khronos.webgl.Uint8Array

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
                algorithm = hmacSha512Algorithm(),
                extractable = false,
                keyUsages = mapKeyUsages("sign"),
            ).await()
    val digest =
        crypto
            .sign(
                algorithm = hmacAlgorithm(),
                key = key,
                data = data.toUint8Array(),
            ).await()

    return Uint8Array(digest).toByteArray()
}

private fun hmacSha512Algorithm(): JsAny = js("""({ "name": "HMAC", "hash": "SHA-512" })""")

private fun hmacAlgorithm(): JsAny = js("""({ "name": "HMAC" })""")
