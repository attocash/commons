package cash.atto.commons

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class AttoHeight(
    val value: ULong,
) : Comparable<AttoHeight> {
    override operator fun compareTo(other: AttoHeight): Int = value.compareTo(other.value)

    operator fun plus(uLong: ULong): AttoHeight = AttoHeight(value + uLong)
    operator fun plus(uInt: UInt): AttoHeight = AttoHeight(value + uInt)
    operator fun plus(height: AttoHeight): AttoHeight = AttoHeight(value + height.value)

    operator fun minus(uLong: ULong): AttoHeight = AttoHeight(value - uLong)
    operator fun minus(uInt: UInt): AttoHeight = AttoHeight(value - uInt)
    operator fun minus(height: AttoHeight): AttoHeight = AttoHeight(value - height.value)
}

fun ULong.toAttoHeight() = AttoHeight(this)

fun UInt.toAttoHeight() = this.toULong().toAttoHeight()
