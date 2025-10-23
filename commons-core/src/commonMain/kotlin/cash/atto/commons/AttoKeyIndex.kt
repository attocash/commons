package cash.atto.commons

data class AttoKeyIndex(
    val value: UInt,
) {
    fun toInt(): Int = value.toInt()
}

operator fun AttoKeyIndex.compareTo(other: AttoKeyIndex) = value.compareTo(other.value)

operator fun AttoKeyIndex.inc() = AttoKeyIndex(value + 1U)

fun UInt.toAttoIndex(): AttoKeyIndex = AttoKeyIndex(this)

fun Int.toAttoIndex(): AttoKeyIndex = this.toUInt().toAttoIndex()
