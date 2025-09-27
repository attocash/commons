package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
enum class AttoUnit(
    val prefix: String,
    internal val scale: UByte,
) {
    ATTO("atto", 9U),
    RAW("raw", 0U),
}

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
@Serializable(with = AttoAmountAsULongSerializer::class)
data class AttoAmount(
    val raw: ULong,
) : Comparable<AttoAmount> {
    init {
        if (raw > MAX_RAW) {
            throw IllegalStateException("$raw exceeds the max amount of $MAX_RAW")
        }
    }

    companion object {
        private val MAX_RAW = 18_000_000_000_000_000_000UL
        val MAX = AttoAmount(MAX_RAW)
        private val MIN_RAW = 0UL
        val MIN = AttoAmount(MIN_RAW)

        private fun ULong.pow(exponent: Int): ULong {
            var result = 1UL
            repeat(exponent) {
                result *= this
            }
            return result
        }

        private fun scaleFactor(scale: UByte): ULong {
            return 10UL.pow(scale.toInt())
        }

        fun from(
            unit: AttoUnit,
            string: String,
        ): AttoAmount {
            val parts = string.split('.')
            val wholePart = parts[0].toULong()
            val factor = scaleFactor(unit.scale)

            var scaledValue = wholePart * factor

            if (parts.size > 1) {
                val fractionalPart = parts[1].toULong()
                scaledValue += fractionalPart * factor / scaleFactor(parts[1].length.toUByte())
            }
            return AttoAmount(scaledValue)
        }
    }

    @JsName("toFormattedString")
    fun toString(unit: AttoUnit): String {
        val factor = scaleFactor(unit.scale)
        val wholePart = raw / factor

        val fractionalPart = raw % factor

        return if (fractionalPart == 0UL) {
            wholePart.toString()
        } else {
            val fractionalStr = fractionalPart.toString().padStart(unit.scale.toInt(), '0').trimEnd('0')
            "$wholePart.$fractionalStr"
        }
    }

    operator fun plus(amount: AttoAmount): AttoAmount {
        val total = raw + amount.raw
        if (total < raw || total < amount.raw) {
            throw IllegalStateException("ULong overflow")
        }
        return AttoAmount(total)
    }

    operator fun minus(amount: AttoAmount): AttoAmount {
        val total = raw - amount.raw
        if (total > raw) {
            throw IllegalStateException("ULong underflow")
        }
        return AttoAmount(total)
    }

    override operator fun compareTo(other: AttoAmount): Int = this.raw.compareTo(other.raw)

    override fun toString(): String = "$raw"
}

fun ULong.toAttoAmount(): AttoAmount = AttoAmount(this)

fun String.toAttoAmount(): AttoAmount = AttoAmount(this.toULong())

object AttoAmountAsULongSerializer : KSerializer<AttoAmount> {
    override val descriptor = ULong.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoAmount,
    ) {
        ULong.serializer().serialize(encoder, value.raw)
    }

    override fun deserialize(decoder: Decoder): AttoAmount = AttoAmount(ULong.serializer().deserialize(decoder))
}
