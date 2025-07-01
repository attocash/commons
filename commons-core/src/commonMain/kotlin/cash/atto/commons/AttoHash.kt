package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JsExportForJs
@Serializable(with = AttoHashSerializer::class)
data class AttoHash(
    val value: ByteArray,
) {
    companion object {
        fun parse(value: String): AttoHash {
            return AttoHash(value.fromHexToByteArray())
        }

        fun hash(
            size: Int,
            vararg byteArrays: ByteArray,
        ): AttoHash {
            return AttoHash(AttoHasher.hash(size, * byteArrays))
        }

        fun hashVote(
            blockHash: AttoHash,
            algorithm: AttoAlgorithm,
            timestamp: Instant,
        ): AttoHash {
            return hash(
                32,
                blockHash.value,
                byteArrayOf(algorithm.code.toByte()),
                timestamp.toByteArray(),
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

    override fun toString(): String = value.toHex()

    fun isValid(): Boolean = AttoAlgorithm.entries.any { it.hashSize == value.size }
}

@JsExportForJs
interface AttoHashable {
    val hash: AttoHash
}

object AttoHashSerializer : KSerializer<AttoHash> {
    override val descriptor = PrimitiveSerialDescriptor("AttoHash", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoHash,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoHash = AttoHash.parse(decoder.decodeString())
}
