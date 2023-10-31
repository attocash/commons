package cash.atto.commons

import org.bouncycastle.crypto.digests.Blake2bDigest

//TODO: There a multiple type of hashes. Split this class in multiple implementation (i.e.: AttoWorkHash, AttoBlockHash, AttoPublicKeyHash etc)
data class AttoHash(val value: ByteArray, val size: Int = defaultSize) {

    init {
        value.checkLength(size)
    }

    companion object {
        val defaultSize = 32

        fun parse(value: String): AttoHash {
            return AttoHash(value.fromHexToByteArray())
        }

        fun hash(size: Int, vararg byteArrays: ByteArray): AttoHash {
            val blake2b = Blake2bDigest(null, size, null, null)
            for (byteArray in byteArrays) {
                blake2b.update(byteArray, 0, byteArray.size)
            }
            val output = ByteArray(size)
            blake2b.doFinal(output, 0)
            return AttoHash(output, size)
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