package cash.atto.commons

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Serializable(with = AttoWorkTargetAsStringSerializer::class)
data class AttoWorkTarget(
    val value: ByteArray,
) {
    companion object {
        const val SIZE = 32
    }

    init {
        value.checkLength(SIZE)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AttoWorkTarget

        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = value.toHex()
}

@Deprecated(
    "Moved to AttoBlock.getTarget(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.getTarget()"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoBlock.getTarget(): AttoWorkTarget = this.getTarget()

@OptIn(ExperimentalJsExport::class)
@JsExport.Ignore
@Deprecated(
    "Moved to AttoWork.isValid(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoWork.isValid(threshold, target, work)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoWork.Companion.isValid(
    threshold: ULong,
    target: AttoWorkTarget,
    work: ByteArray,
): Boolean = AttoWork.isValid(threshold, target, work)

@Deprecated(
    "Moved to AttoWork.isValid(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoWork.isValid(network, timestamp, target, work)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoWork.Companion.isValid(
    network: AttoNetwork,
    timestamp: AttoInstant,
    target: AttoWorkTarget,
    work: ByteArray,
): Boolean = AttoWork.isValid(network, timestamp, target, work)

object AttoWorkTargetAsStringSerializer : KSerializer<AttoWorkTarget> {
    override val descriptor = PrimitiveSerialDescriptor("AttoWorkTargetAsString", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoWorkTarget,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoWorkTarget =
        AttoWorkTarget(decoder.decodeString().fromHexToByteArray(AttoWorkTarget.SIZE))
}
