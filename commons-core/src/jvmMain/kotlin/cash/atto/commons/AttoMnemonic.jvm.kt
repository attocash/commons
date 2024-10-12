package cash.atto.commons

import java.security.MessageDigest

internal actual fun checksum(entropy: ByteArray): Byte {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(entropy, 0, 32)

    val checksum = digest.digest()
    return checksum[0]
}
