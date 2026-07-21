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
    ) : Comparable<AttoKeyIndex> {
        @JsExport.Ignore
        override operator fun compareTo(other: AttoKeyIndex): Int = value.compareTo(other.value)

        @JsExport.Ignore
        operator fun inc(): AttoKeyIndex = AttoKeyIndex(value + 1U)

        fun toInt(): Int = value.toInt()
    }

@JvmSynthetic
@Deprecated(
    "Moved to AttoKeyIndex.compareTo(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.compareTo(other)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
operator fun AttoKeyIndex.compareTo(other: AttoKeyIndex): Int = this.compareTo(other)

@JvmSynthetic
@Deprecated(
    "Moved to AttoKeyIndex.inc(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.inc()"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
operator fun AttoKeyIndex.inc(): AttoKeyIndex = this.inc()

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
