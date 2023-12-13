package cash.atto.commons

import org.bouncycastle.crypto.digests.Blake2bDigest


internal fun hashRaw(size: Int, vararg byteArrays: ByteArray): ByteArray {
    val blake2b = Blake2bDigest(null, size, null, null)
    for (byteArray in byteArrays) {
        blake2b.update(byteArray, 0, byteArray.size)
    }
    val output = ByteArray(size)
    blake2b.doFinal(output, 0)
    return output
}

data class AttoHash(val value: ByteArray) {

    val size = value.size

    companion object {
        fun parse(value: String): AttoHash {
            return AttoHash(value.fromHexToByteArray())
        }

        fun hash(size: Int, vararg byteArrays: ByteArray): AttoHash {
            return AttoHash(hashRaw(size, * byteArrays))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttoHash

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    override fun toString(): String {
        return value.toHex()
    }
}