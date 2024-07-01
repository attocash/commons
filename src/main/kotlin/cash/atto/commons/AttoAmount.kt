package cash.atto.commons

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.math.BigInteger

enum class AttoUnit(
    val prefix: String,
    internal val multiplier: BigDecimal,
) {
    ATTO("atto", BigDecimal(1_000_000_000)),
    RAW("raw", BigDecimal(1)),
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

        fun from(
            unit: AttoUnit,
            bigDecimal: BigDecimal,
        ): AttoAmount {
            return bigDecimal.multiply(unit.multiplier).toAttoAmount()
        }
    }

    fun toBigDecimal(unit: AttoUnit): BigDecimal {
        return BigDecimal(raw.toString()).divide(unit.multiplier)
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

fun BigInteger.toAttoAmount(): AttoAmount = AttoAmount(toString().toULong())

fun BigDecimal.toAttoAmount(): AttoAmount = AttoAmount(toString().toULong())

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
