@file:OptIn(ExperimentalTime::class)

package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.JsExport
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@JsExportForJs
@Serializable(with = AttoInstantAsLongSerializer::class)
data class AttoInstant(
    val value: Instant,
) : Comparable<AttoInstant> {
    companion object {
        @JsExport.Ignore
        fun now(clock: Clock): AttoInstant {
            return fromEpochMilliseconds(clock.now().toEpochMilliseconds())
        }

        /**
         * To avoid opt in warn
         */
        fun now(): AttoInstant {
            return now(Clock.System)
        }

        fun fromIso(value: String): AttoInstant {
            return fromEpochMilliseconds(Instant.parse(value).toEpochMilliseconds())
        }

        fun fromEpochMilliseconds(value: Long): AttoInstant {
            return AttoInstant(Instant.fromEpochMilliseconds(value))
        }
    }

    @JsExport.Ignore
    operator fun plus(duration: Duration): AttoInstant {
        return AttoInstant(value.plus(duration))
    }

    @JsExport.Ignore
    operator fun minus(duration: Duration): AttoInstant {
        return AttoInstant(value.minus(duration))
    }

    @JsExport.Ignore
    operator fun minus(another: AttoInstant): Duration = value - another.value

    @JsExport.Ignore
    override fun compareTo(other: AttoInstant): Int = value.compareTo(other.value)

    fun toEpochMilliseconds(): Long = value.toEpochMilliseconds()

    override fun toString(): String = value.toString()
}

fun Instant.toAtto(): AttoInstant = AttoInstant(this)

object AttoInstantAsLongSerializer : KSerializer<AttoInstant> {
    override val descriptor: SerialDescriptor = Long.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoInstant,
    ) {
        encoder.encodeLong(value.value.toEpochMilliseconds())
    }

    override fun deserialize(decoder: Decoder): AttoInstant = AttoInstant(Instant.fromEpochMilliseconds(decoder.decodeLong()))
}

object AttoInstantAsStringSerializer : KSerializer<AttoInstant> {
    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoInstant,
    ) {
        val iso = Instant.fromEpochMilliseconds(value.value.toEpochMilliseconds()).toString()
        encoder.encodeString(iso)
    }

    override fun deserialize(decoder: Decoder): AttoInstant = AttoInstant(Instant.parse(decoder.decodeString()))
}
