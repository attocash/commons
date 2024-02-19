package cash.atto.commons

import org.bouncycastle.util.encoders.Base32


private const val PREFIX = "atto:"

private fun ByteArray.toAddress(): String {
    require(this.size == 38) { "ByteArray should have 38 bytes" }
    return PREFIX + String(Base32.encode(this)).replace("=", "").lowercase()
}

private fun String.fromAddress(): ByteArray {
    return Base32.decode(this.substring(PREFIX.length).uppercase() + "===")
}

data class AttoAddress(val algorithmPublicKey: AttoAlgorithmPublicKey) {
    val algorithm = algorithmPublicKey.algorithm
    val publicKey = algorithmPublicKey.publicKey
    val value = toAddress(algorithmPublicKey)

    constructor(algorithm: AttoAlgorithm, publicKey: AttoPublicKey) : this(AttoAlgorithmPublicKey(algorithm, publicKey))

    companion object {
        private val regex = "^$PREFIX[a-z2-7]{61}$".toRegex()
        private const val CHECKSUM_SIZE = 5;

        private fun checksum(algorithmPublicKey: AttoAlgorithmPublicKey): ByteArray {
            return hashRaw(
                CHECKSUM_SIZE,
                byteArrayOf(algorithmPublicKey.algorithm.code.toByte()),
                algorithmPublicKey.publicKey.value
            )
        }

        private fun toAlgorithmPublicKey(decoded: ByteArray): AttoAlgorithmPublicKey {
            val algorithm = AttoAlgorithm.from(decoded[0].toUByte())
            val publicKey = AttoPublicKey(decoded.sliceArray(1 until 33))

            return AttoAlgorithmPublicKey(algorithm, publicKey)
        }

        private fun toAlgorithmPublicKey(value: String): AttoAlgorithmPublicKey {
            return toAlgorithmPublicKey(value.fromAddress())
        }

        fun isValid(value: String): Boolean {
            if (!regex.matches(value)) {
                return false
            }
            val decoded = value.fromAddress()

            val algorithmPublicKey = toAlgorithmPublicKey(decoded)
            val checksum = decoded.sliceArray(33 until decoded.size)

            return checksum.contentEquals(checksum(algorithmPublicKey))
        }

        fun toAddress(algorithmPublicKey: AttoAlgorithmPublicKey): String {
            val algorithm = byteArrayOf(algorithmPublicKey.algorithm.code.toByte())
            val publicKey = algorithmPublicKey.publicKey.value
            val checksum = checksum(algorithmPublicKey)

            return (algorithm + publicKey + checksum).toAddress()
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