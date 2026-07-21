package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import cash.atto.commons.utils.SecureRandom
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExportForJs
@Serializable(with = AttoChallengeSerializer::class)
data class AttoChallenge(
    val value: ByteArray,
) {
    companion object {
        @OptIn(ExperimentalJsExport::class)
        @JsExport.Ignore
        @JvmStatic
        fun generate(size: UInt = 64U): AttoChallenge = AttoChallenge(SecureRandom.randomByteArray(size))
    }

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

@Deprecated(
    "Moved to AttoChallenge.generate(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoChallenge.generate(size)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoChallenge.Companion.generate(size: UInt = 64U): AttoChallenge = AttoChallenge.generate(size)

object AttoChallengeSerializer : KSerializer<AttoChallenge> {
    override val descriptor = PrimitiveSerialDescriptor("AttoChallenge", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoChallenge,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoChallenge = AttoChallenge(decoder.decodeString().fromHexToByteArrayAtLeast(64))
}
