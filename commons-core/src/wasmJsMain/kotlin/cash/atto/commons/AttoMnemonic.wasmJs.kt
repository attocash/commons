package cash.atto.commons

import cash.atto.commons.utils.SHA256

internal actual fun checksum(entropy: ByteArray): Byte {
    val hasher = SHA256()
    hasher.update(entropy.toUint8Array().subarray(0, 32))
    val hash = hasher.digest().toByteArray()
    return hash[0]
}
