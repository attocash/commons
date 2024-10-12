package cash.atto.commons

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

actual fun generateSecretWithPBKDF2WithHmacSHA512(
    mnemonic: CharArray,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int
): ByteArray {
    val spec = PBEKeySpec(mnemonic, salt, iterations, keyLength)

    val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
    return skf.generateSecret(spec).encoded
}
