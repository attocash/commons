package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import cash.atto.commons.utils.SecureRandom
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JsExportForJs
@Serializable(with = AttoChallengeSerializer::class)
data class AttoChallenge(
    val value: ByteArray,
) {
    companion object {}

    init {
        require(value.size >= 64) { "Challenge should have at least 64 bytes" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AttoChallenge

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = value.toHex()
}

fun AttoChallenge.Companion.generate(size: UInt = 64U): AttoChallenge = AttoChallenge(SecureRandom.randomByteArray(size))

object AttoChallengeSerializer : KSerializer<AttoChallenge> {
    override val descriptor = PrimitiveSerialDescriptor("AttoChallenge", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoChallenge,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoChallenge = AttoChallenge(decoder.decodeString().fromHexToByteArray())
}
