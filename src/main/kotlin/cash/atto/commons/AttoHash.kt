package cash.atto.commons

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
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

@Serializable
data class AttoHash(val value: ByteArray) {

    companion object {
        fun parse(value: String): AttoHash {
            return AttoHash(value.fromHexToByteArray())
        }

        fun hash(size: Int, vararg byteArrays: ByteArray): AttoHash {
            return AttoHash(hashRaw(size, * byteArrays))
        }

        fun hashVote(blockHash: AttoHash, algorithm: AttoAlgorithm, timestamp: Instant): AttoHash {
            return AttoHash.hash(
                32,
                blockHash.value,
                byteArrayOf(algorithm.code.toByte()),
                timestamp.toByteArray()
            )
        }
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttoHash) return false

        return value.contentEquals(other.value)
    }

    override fun toString(): String {
        return value.toHex()
    }

    fun isValid(): Boolean {
        return AttoAlgorithm.entries.any { it.hashSize == value.size }
    }
}