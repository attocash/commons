package cash.atto.commons

import cash.atto.commons.utils.SubtleCrypto
import cash.atto.commons.utils.getInstance
import org.khronos.webgl.Uint8Array
import kotlin.js.json

actual suspend fun generateSecretWithPBKDF2WithHmacSHA512(
    mnemonic: CharArray,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int,
): ByteArray {
    val crypto = SubtleCrypto.getInstance()

    val passwordString = mnemonic.concatToString()

    val baseKey =
        crypto
            .importKey(
                format = "raw",
                keyData = passwordString.encodeToByteArray().toUint8Array(),
                algorithm = json("name" to "PBKDF2"),
                extractable = false,
                keyUsages = arrayOf("deriveBits"),
            ).await()

    val algorithm =
        json(
            "name" to "PBKDF2",
            "hash" to "SHA-512",
            "salt" to salt.toUint8Array(),
            "iterations" to iterations,
        )

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
