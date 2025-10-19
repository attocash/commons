package cash.atto.commons

data class AttoKeyIndex(
    val value: UInt,
) {
    fun toInt(): Int = value.toInt()
}

fun UInt.toAttoIndex(): AttoKeyIndex = AttoKeyIndex(this)

fun Int.toAttoIndex(): AttoKeyIndex = this.toUInt().toAttoIndex()
