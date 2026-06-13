@file:JvmName("AttoAmounts")
@file:OptIn(ExperimentalJsStatic::class)

package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
enum class AttoUnit(
    val prefix: String,
    @JsExport.Ignore
    internal val scale: UByte,
) {
    ATTO("atto", 9U),
    RAW("raw", 0U),
}

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
@Serializable(with = AttoAmountAsULongSerializer::class)
data class AttoAmount
    @JsExport.Ignore
    constructor(
        @JsExport.Ignore
        val raw: ULong,
    ) : Comparable<AttoAmount> {
        init {
            if (raw > MAX_RAW) {
                throw IllegalStateException("$raw exceeds the max amount of $MAX_RAW")
            }
        }

        companion object {
            private val MAX_RAW = 18_000_000_000_000_000_000UL

            @JvmField
            @JsStatic
            val MAX = AttoAmount(MAX_RAW)

            @JsExport.Ignore
            private val MIN_RAW = 0UL

            @JvmField
            @JsStatic
            val MIN = AttoAmount(MIN_RAW)

            private fun ULong.pow(exponent: Int): ULong {
                var result = 1UL
                repeat(exponent) {
                    result *= this
                }
                return result
            }

            private fun scaleFactor(scale: UByte): ULong = 10UL.pow(scale.toInt())

            private fun requireDecimalDigits(
                value: String,
                partName: String,
            ): ULong {
                require(value.isNotEmpty()) { "$partName is empty" }
                require(value.all { it in '0'..'9' }) { "$partName contains non-decimal characters" }
                return value.toULongOrNull() ?: throw IllegalArgumentException("$partName exceeds ULong.MAX_VALUE")
            }

            @OptIn(ExperimentalJsStatic::class)
            @JsStatic
            @JvmStatic
            fun from(
                unit: AttoUnit,
                string: String,
            ): AttoAmount {
                val parts = string.split('.')
                require(parts.size <= 2) { "Amount contains multiple decimal separators" }

                val wholePart = requireDecimalDigits(parts[0], "Whole part")
                val factor = scaleFactor(unit.scale)
                require(wholePart <= MAX_RAW / factor) { "$string exceeds the max amount of $MAX_RAW raw" }

                val wholeValue = wholePart * factor

                val fractionalValue =
                    if (parts.size == 1) {
                        0UL
                    } else {
                        val fractionalString = parts[1]
                        require(unit.scale.toInt() > 0) { "${unit.prefix} does not support fractional values" }
                        require(fractionalString.length <= unit.scale.toInt()) {
                            "Fractional part exceeds ${unit.scale} decimal places"
                        }
                        val fractionalPart = requireDecimalDigits(fractionalString, "Fractional part")
                        val remainingScale = unit.scale.toInt() - fractionalString.length
                        fractionalPart * scaleFactor(remainingScale.toUByte())
                    }

                require(fractionalValue <= MAX_RAW - wholeValue) { "$string exceeds the max amount of $MAX_RAW raw" }

                return AttoAmount(wholeValue + fractionalValue)
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

@JvmSynthetic
fun ULong.toAttoAmount() = AttoAmount(this)

@JvmSynthetic
fun UInt.toAttoAmount() = this.toULong().toAttoAmount()

fun Int.toAttoAmount() = this.toULong().toAttoAmount()

fun Long.toAttoAmount() = this.toULong().toAttoAmount()

@JsExportForJs
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
