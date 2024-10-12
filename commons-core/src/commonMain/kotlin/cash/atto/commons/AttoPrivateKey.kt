package cash.atto.commons

import cash.atto.commons.utils.SecureRandom

expect fun ed25519BIP44(seed: AttoSeed, path: String): ByteArray

class AttoPrivateKey(
    val value: ByteArray,
) {
    init {
        value.checkLength(32)
    }

    constructor(seed: AttoSeed, index: UInt) : this(ed25519BIP44(seed, "m/44'/$coinType'/$index'"))

    companion object {
        private val coinType = 1869902945 // "atto".toByteArray().toUInt()

        fun parse(value: String): AttoPrivateKey {
            return AttoPrivateKey(value.fromHexToByteArray())
        }

        fun generate(): AttoPrivateKey {
            val value = SecureRandom.randomByteArray(32U)
            return AttoPrivateKey(value)
        }
    }

    override fun toString(): String {
        return "${value.size} bytes"
    }
}

fun AttoSeed.toPrivateKey(index: UInt): AttoPrivateKey {
    return AttoPrivateKey(this, index)
}

class AttoAlgorithmPrivateKey(
    val algorithm: AttoAlgorithm,
    val privateKey: AttoPrivateKey,
) {
    val value = byteArrayOf(algorithm.code.toByte()) + privateKey.value

    init {
        privateKey.value.checkLength(algorithm.privateKeySize)
    }

    companion object {
        fun parse(value: String): AttoAlgorithmPrivateKey {
            val byteArray = value.fromHexToByteArray()
            val algorithm = AttoAlgorithm.from(byteArray[0].toUByte())
            val privateKey = AttoPrivateKey(byteArray.sliceArray(1 until byteArray.size))
            return AttoAlgorithmPrivateKey(algorithm, privateKey)
        }
    }

    override fun toString(): String {
        return "${value.size} bytes"
    }
}
