@file:JvmName("AttoKeyIndexes")

package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlin.jvm.JvmName
import kotlin.jvm.JvmSynthetic

@JsExportForJs
data class AttoKeyIndex(
    val value: UInt,
) {
    fun toInt(): Int = value.toInt()
}

@JvmSynthetic
operator fun AttoKeyIndex.compareTo(other: AttoKeyIndex) = value.compareTo(other.value)

@JvmSynthetic
operator fun AttoKeyIndex.inc() = AttoKeyIndex(value + 1U)

@JvmSynthetic
fun UInt.toAttoIndex(): AttoKeyIndex = AttoKeyIndex(this)

@JsExportForJs
fun Int.toAttoIndex(): AttoKeyIndex = this.toUInt().toAttoIndex()
