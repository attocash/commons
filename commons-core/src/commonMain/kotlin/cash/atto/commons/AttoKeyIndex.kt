@file:JvmName("AttoKeyIndexes")

package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmName
import kotlin.jvm.JvmSynthetic

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
@Serializable(with = AttoKeyIndexSerializer::class)
data class AttoKeyIndex
    @JsExport.Ignore
    constructor(
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

object AttoKeyIndexSerializer : KSerializer<AttoKeyIndex> {
    override val descriptor = UInt.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoKeyIndex,
    ) {
        UInt.serializer().serialize(encoder, value.value)
    }

    override fun deserialize(decoder: Decoder): AttoKeyIndex = AttoKeyIndex(UInt.serializer().deserialize(decoder))
}
