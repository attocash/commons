package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
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

fun AttoBlock.getTarget(): AttoWorkTarget =
    when (this) {
        is AttoOpenBlock -> AttoWorkTarget(this.publicKey.value)
        is PreviousSupport -> AttoWorkTarget(this.previous.value)
    }

@OptIn(ExperimentalJsExport::class)
@JsExport.Ignore
fun AttoWork.Companion.isValid(
    threshold: ULong,
    target: AttoWorkTarget,
    work: ByteArray,
): Boolean = isValid(threshold, target.value, work)

fun AttoWork.Companion.isValid(
    network: AttoNetwork,
    timestamp: AttoInstant,
    target: AttoWorkTarget,
    work: ByteArray,
): Boolean {
    if (timestamp < INITIAL_INSTANT) {
        return false
    }
    return isValid(AttoWork.getThreshold(network, timestamp), target, work)
}

object AttoWorkTargetAsStringSerializer : KSerializer<AttoWorkTarget> {
    override val descriptor = PrimitiveSerialDescriptor("AttoWorkTargetAsString", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoWorkTarget,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoWorkTarget = AttoWorkTarget(decoder.decodeString().fromHexToByteArray())
}
