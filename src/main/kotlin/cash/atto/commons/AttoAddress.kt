package cash.atto.commons

import org.bouncycastle.util.encoders.Base32

private fun ByteArray.toBase32(): String {
    return String(Base32.encode(this)).replace("=", "").lowercase()
}

private fun String.fromBase32(): ByteArray {
    return Base32.decode(this.uppercase() + "===")
}

data class AttoAddress(val algorithmPublicKey: AttoAlgorithmPublicKey) {
    val algorithm = algorithmPublicKey.algorithm
    val publicKey = algorithmPublicKey.publicKey
    val value = toAddress(algorithmPublicKey)

    constructor(algorithm: AttoAlgorithm, publicKey: AttoPublicKey) : this(AttoAlgorithmPublicKey(algorithm, publicKey))

    companion object {
        private val prefix = "atto_"
        private val regex = "^$prefix[a-z2-7]{61}$".toRegex()

        private fun checksum(algorithmPublicKey: AttoAlgorithmPublicKey): ByteArray {
            return hashRaw(
                5,
                byteArrayOf(algorithmPublicKey.algorithm.code.toByte()),
                algorithmPublicKey.publicKey.value
            )
        }

        private fun toAlgorithmPublicKey(value: String): AttoAlgorithmPublicKey {
            val decoded = value.substring(prefix.length, value.length - 8).fromBase32()
            val algorithm = AttoAlgorithm.from(decoded[0].toUByte())
            val publicKey = AttoPublicKey(decoded.sliceArray(1 until decoded.size))

            return AttoAlgorithmPublicKey(algorithm, publicKey)
        }

        fun isValid(value: String): Boolean {
            if (!regex.matches(value)) {
                return false
            }
            val expectedEncodedChecksum = value.substring(value.length - 8)

            val algorithmPublicKey = toAlgorithmPublicKey(value)

            val checksum = checksum(algorithmPublicKey)
            val encodedChecksum = checksum.toBase32()
            return expectedEncodedChecksum == encodedChecksum
        }

        fun toAddress(algorithmPublicKey: AttoAlgorithmPublicKey): String {
            val checksum = checksum(algorithmPublicKey)

            val encodedPublicKey = algorithmPublicKey.publicKey.value.toBase32()
            val encodedChecksum = checksum.toBase32()
            return prefix + encodedPublicKey + encodedChecksum
        }

        fun parse(value: String): AttoAddress {
            require(isValid(value)) { "$value is invalid" }

            val algorithmPublicKey = toAlgorithmPublicKey(value)

            return AttoAddress(algorithmPublicKey)
        }
    }

    override fun toString(): String {
        return value
    }
}

fun AttoPublicKey.toAddress(algorithm: AttoAlgorithm): AttoAddress {
    return AttoAddress(algorithm, this)
}