package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
@Serializable(with = AttoHeightSerializer::class)
data class AttoHeight(
    val value: ULong,
) : Comparable<AttoHeight> {
    companion object {
        val MIN = AttoHeight(1U)
        val MAX = AttoHeight(ULong.MAX_VALUE)
    }

    override operator fun compareTo(other: AttoHeight): Int = value.compareTo(other.value)

    @JsExport.Ignore
    operator fun plus(uLong: ULong): AttoHeight = AttoHeight(value + uLong)

    @JsExport.Ignore
    operator fun plus(uInt: UInt): AttoHeight = AttoHeight(value + uInt)

    operator fun plus(height: AttoHeight): AttoHeight = AttoHeight(value + height.value)

    @JsExport.Ignore
    operator fun minus(uLong: ULong): AttoHeight = AttoHeight(value - uLong)

    @JsExport.Ignore
    operator fun minus(uInt: UInt): AttoHeight = AttoHeight(value - uInt)

    operator fun minus(height: AttoHeight): AttoHeight = AttoHeight(value - height.value)

    fun next(): AttoHeight = AttoHeight(value + 1u)

    override fun toString(): String = value.toString()
}

fun ULong.toAttoHeight() = AttoHeight(this)

fun UInt.toAttoHeight() = this.toULong().toAttoHeight()

object AttoHeightSerializer : KSerializer<AttoHeight> {
    override val descriptor = ULong.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoHeight,
    ) {
        ULong.serializer().serialize(encoder, value.value)
    }

    override fun deserialize(decoder: Decoder): AttoHeight = AttoHeight(ULong.serializer().deserialize(decoder))
}
