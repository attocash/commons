@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons

import cash.atto.commons.utils.getSubtleCryptoInstance
import org.khronos.webgl.Uint8Array

internal fun pbkdf2Algorithm(): JsAny = js("""({ "name": "pbkdf2" })""")

internal fun pbkdf2AndSha512Algorithm(
    salt: Uint8Array,
    iterations: Int,
): JsAny = js("""({ "name": "pbkdf2", "hash": "SHA-512", "salt": salt, "iterations": iterations })""")

actual suspend fun generateSecretWithPBKDF2WithHmacSHA512(
    mnemonic: CharArray,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int,
): ByteArray {
    val crypto = getSubtleCryptoInstance()

    val passwordString = mnemonic.concatToString()

    val baseKey =
        crypto
            .importKey(
                format = "raw",
                keyData = passwordString.encodeToByteArray().toUint8Array(),
                algorithm = pbkdf2Algorithm(),
                extractable = false,
                keyUsages = mapKeyUsages(arrayOf("deriveBits")),
            ).await()

    val algorithm = pbkdf2AndSha512Algorithm(salt.toUint8Array(), iterations)

    val derivedBits =
        crypto
            .deriveBits(
                algorithm = algorithm,
                baseKey = baseKey,
                length = keyLength,
            ).await()

    val derivedArray = Uint8Array(derivedBits)
    return derivedArray.toByteArray()
}

private fun mapKeyUsages(keyUsages: Array<String>): JsArray<JsString> =
    JsArray<JsString>().also {
        keyUsages.forEachIndexed { index, value ->
            it[index] = value.toJsString()
        }
    }
