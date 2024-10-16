package cash.atto.commons

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

enum class AttoUnit(
    val prefix: String,
    internal val scale: UByte,
) {
    ATTO("atto", 9U),
    RAW("raw", 0U),
}

@Serializable(with = AttoAmountSerializer::class)
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
            val value = string.toULong()
            val factor = scaleFactor(unit.scale)

            // Check for overflow before performing multiplication
            if (factor != 0UL && value > ULong.MAX_VALUE / factor) {
                throw IllegalStateException("Multiplication overflow: $value * $factor exceeds ULong capacity")
            }

            val scaledValue = value * factor
            return AttoAmount(scaledValue)
        }
    }

    fun toString(unit: AttoUnit): String {
        val factor = scaleFactor(unit.scale)
        val wholePart = raw / factor
        val fractionalPart = raw % factor

        return when (fractionalPart) {
            0UL -> wholePart.toString()
            else -> "$wholePart.$fractionalPart"
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

object AttoAmountSerializer : KSerializer<AttoAmount> {
    override val descriptor = ULong.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoAmount,
    ) {
        ULong.serializer().serialize(encoder, value.raw)
    }

    override fun deserialize(decoder: Decoder): AttoAmount = AttoAmount(ULong.serializer().deserialize(decoder))
}
