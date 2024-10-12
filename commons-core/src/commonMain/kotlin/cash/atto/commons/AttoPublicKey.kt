package cash.atto.commons

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


@Serializable(with = AttoPublicKeySerializer::class)
data class AttoPublicKey(
    val value: ByteArray,
) {
    init {
        value.checkLength(32)
    }

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

expect fun AttoPrivateKey.toPublicKey(): AttoPublicKey

data class AttoAlgorithmPublicKey(
    val algorithm: AttoAlgorithm,
    val publicKey: AttoPublicKey,
) {
    val value = byteArrayOf(algorithm.code.toByte()) + publicKey.value

    init {
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

    override fun toString(): String = value.toHex()
}

object AttoPublicKeySerializer : KSerializer<AttoPublicKey> {
    override val descriptor = PrimitiveSerialDescriptor("AttoPublicKey", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoPublicKey,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoPublicKey = AttoPublicKey.parse(decoder.decodeString())
}
