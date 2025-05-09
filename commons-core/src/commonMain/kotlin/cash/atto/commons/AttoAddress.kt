package cash.atto.commons

import cash.atto.commons.serialiazer.AttoAddressAsByteArraySerializer
import cash.atto.commons.utils.Base32
import kotlinx.io.Buffer
import kotlinx.io.writeUByte
import kotlinx.serialization.Serializable

private const val SCHEMA = "atto://"

private fun ByteArray.toAddress(): String {
    require(this.size == 38) { "ByteArray should have 38 bytes" }
    return Base32.encode(this).replace("=", "").lowercase()
}

private fun String.fromAddress(): ByteArray {
    return Base32.decode(this.substring(SCHEMA.length).uppercase() + "===")
}

@Serializable(with = AttoAddressAsByteArraySerializer::class)
data class AttoAddress(
    val algorithm: AttoAlgorithm,
    val publicKey: AttoPublicKey,
) {
    val schema = SCHEMA
    val path = toAddress(algorithm, publicKey)
    val value = schema + path

    companion object {
        private val regex = "^$SCHEMA[a-z2-7]{61}$".toRegex()
        private const val CHECKSUM_SIZE = 5

        private fun checksum(
            algorithm: ByteArray,
            publicKey: ByteArray,
        ): ByteArray {
            return AttoHasher.hash(
                CHECKSUM_SIZE,
                algorithm,
                publicKey,
            )
        }

        private fun checksum(
            algorithm: AttoAlgorithm,
            publicKey: AttoPublicKey,
        ): ByteArray {
            return checksum(byteArrayOf(algorithm.code.toByte()), publicKey.value)
        }

        private fun toAlgorithmPublicKey(decoded: ByteArray): Pair<AttoAlgorithm, AttoPublicKey> {
            val algorithm = AttoAlgorithm.from(decoded[0].toUByte())
            val publicKey = AttoPublicKey(decoded.sliceArray(1 until 33))

            return algorithm to publicKey
        }

        private fun toAlgorithmPublicKey(value: String): Pair<AttoAlgorithm, AttoPublicKey> {
            return toAlgorithmPublicKey(value.fromAddress())
        }

        fun isValid(value: String): Boolean {
            if (!regex.matches(value)) {
                return false
            }
            val decoded = value.fromAddress()

            val (algorithm, publicKey) = toAlgorithmPublicKey(decoded)
            val checksum = decoded.sliceArray(33 until decoded.size)

            return checksum.contentEquals(checksum(algorithm, publicKey))
        }

        fun isValidPath(path: String): Boolean {
            val value = SCHEMA + path
            return isValid(value)
        }

        fun toAddress(
            algorithm: AttoAlgorithm,
            publicKey: AttoPublicKey,
        ): String {
            val algorithm = byteArrayOf(algorithm.code.toByte())
            val publicKey = publicKey.value
            val checksum = checksum(algorithm, publicKey)

            return (algorithm + publicKey + checksum).toAddress()
        }

        fun parse(serialized: ByteArray): AttoAddress {
            val algorithm = AttoAlgorithm.from(serialized[0].toUByte())
            val publicKey = AttoPublicKey(serialized.sliceArray(1 until serialized.size))
            return AttoAddress(algorithm, publicKey)
        }

        fun parse(value: String): AttoAddress {
            require(isValid(value)) { "$value is invalid" }

            val (algorithm, publicKey) = toAlgorithmPublicKey(value)

            return AttoAddress(algorithm, publicKey)
        }

        fun parsePath(path: String): AttoAddress {
            val value = SCHEMA + path
            return parse(value)
        }
    }

    fun toBuffer(): Buffer =
        Buffer().apply {
            this.writeUByte(algorithm.code)
            this.write(publicKey.value, 0, publicKey.value.size)
        }

    override fun toString(): String = value
}

fun AttoPublicKey.toAddress(algorithm: AttoAlgorithm): AttoAddress = AttoAddress(algorithm, this)
