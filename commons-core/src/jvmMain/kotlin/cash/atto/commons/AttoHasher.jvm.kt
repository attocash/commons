package cash.atto.commons

import org.bouncycastle.crypto.digests.Blake2bDigest

actual object AttoHasher {
    actual fun hash(
        size: Int,
        vararg byteArrays: ByteArray,
    ): ByteArray {
        val blake2b = Blake2bDigest(null, size, null, null)
        for (byteArray in byteArrays) {
            blake2b.update(byteArray, 0, byteArray.size)
        }
        val output = ByteArray(size)
        blake2b.doFinal(output, 0)
        return output
    }
}
