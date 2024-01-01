package cash.atto.commons


import cash.atto.commons.serialiazers.AttoPublicKeySerializer
import kotlinx.serialization.Serializable
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private class AttoBIP44(val key: ByteArray, val secretKeySpec: SecretKeySpec) {

    private constructor(derived: ByteArray) : this(
        derived.copyOfRange(0, 32),
        SecretKeySpec(derived, 32, 32, "HmacSHA512")
    )

    fun derive(value: Int): AttoBIP44 {
        val hmacSha512 = Mac.getInstance("HmacSHA512")
        hmacSha512.init(secretKeySpec)
        hmacSha512.update(0.toByte())

        hmacSha512.update(key, 0, 32)

        val indexBytes = ByteArray(4)
        ByteBuffer.wrap(indexBytes).order(ByteOrder.BIG_ENDIAN).putInt(value)
        indexBytes[0] = (indexBytes[0].toInt() or 128.toByte().toInt()).toByte() //hardened

        hmacSha512.update(indexBytes, 0, indexBytes.size)

        val derived = ByteArray(64)
        hmacSha512.doFinal(derived, 0)

        return AttoBIP44(derived)
    }


    companion object {
        fun ed25519(seed: AttoSeed, path: String): ByteArray {
            val hmacSha512 = Mac.getInstance("HmacSHA512")

            hmacSha512.init(SecretKeySpec("ed25519 seed".toByteArray(StandardCharsets.UTF_8), "HmacSHA512"))
            hmacSha512.update(seed.value, 0, seed.value.size)

            val values = path.split("/").asSequence()
                .map { it.trim() }
                .filter { !"M".equals(it, ignoreCase = true) }
                .map { it.replace("'", "").toInt() }
                .toList()

            var bip44 = AttoBIP44(hmacSha512.doFinal())
            for (v in values) {
                bip44 = bip44.derive(v)
            }

            return bip44.key
        }
    }
}

class AttoPrivateKey(val value: ByteArray) {
    init {
        value.checkLength(32)
    }

    constructor(seed: AttoSeed, index: UInt) : this(AttoBIP44.ed25519(seed, "m/44'/${coinType}'/${index}'"))

    companion object {
        private val coinType = 1869902945 // "atto".toByteArray().toUInt()

        fun parse(value: String): AttoPrivateKey {
            return AttoPrivateKey(value.fromHexToByteArray())
        }

        fun generate(): AttoPrivateKey {
            val random = SecureRandom.getInstanceStrong()
            val value = ByteArray(32)
            random.nextBytes(value)
            return AttoPrivateKey(value)
        }
    }

    fun toPublicKey(): AttoPublicKey {
        return AttoPublicKey(this)
    }

    override fun toString(): String {
        return "${value.size} bytes"
    }
}

@Serializable(with = AttoPublicKeySerializer::class)
data class AttoPublicKey(val value: ByteArray) {
    init {
        value.checkLength(32)
    }

    constructor(privateKey: AttoPrivateKey) : this(
        Ed25519PrivateKeyParameters(
            privateKey.value,
            0
        ).generatePublicKey().encoded
    )

    companion object {
        fun parse(value: String): AttoPublicKey {
            return AttoPublicKey(value.fromHexToByteArray())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttoPublicKey) return false

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    override fun toString(): String {
        return value.toHex()
    }
}

fun AttoSeed.toPrivateKey(index: UInt): AttoPrivateKey {
    return AttoPrivateKey(this, index)
}


class AttoAlgorithmPrivateKey(val algorithm: AttoAlgorithm, val privateKey: AttoPrivateKey) {
    val value = byteArrayOf(algorithm.code.toByte()) + privateKey.value

    init {
        require(algorithm != AttoAlgorithm.UNKNOWN) { "Algorithm can't be $algorithm" }
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

data class AttoAlgorithmPublicKey(val algorithm: AttoAlgorithm, val publicKey: AttoPublicKey) {
    val value = byteArrayOf(algorithm.code.toByte()) + publicKey.value

    init {
        require(algorithm != AttoAlgorithm.UNKNOWN) { "Algorithm can't be $algorithm" }
        publicKey.value.checkLength(algorithm.publicKeySize)
    }

    companion object {
        fun parse(value: String): AttoAlgorithmPublicKey {
            val byteArray = value.fromHexToByteArray()
            val algorithm = AttoAlgorithm.from(byteArray[0].toUByte())
            val publicKey = AttoPublicKey(byteArray.sliceArray(1 until byteArray.size))
            return AttoAlgorithmPublicKey(algorithm, publicKey)
        }
    }

    override fun toString(): String {
        return value.toHex()
    }
}