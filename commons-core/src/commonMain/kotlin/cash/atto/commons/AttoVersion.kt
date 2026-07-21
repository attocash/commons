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
@Serializable(with = AttoVersionSerializer::class)
data class AttoVersion
    @JsExport.Ignore
    constructor(
        val value: UShort,
    ) : Comparable<AttoVersion> {
        override operator fun compareTo(other: AttoVersion): Int = value.compareTo(other.value)

        @JsExport.Ignore
        fun max(anotherVersion: AttoVersion): AttoVersion =
            if (value > anotherVersion.value) {
                this
            } else {
                anotherVersion
            }

        override fun toString(): String = value.toString()
    }

@Deprecated(
    "Moved to AttoVersion.max(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.max(anotherVersion)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoVersion.max(anotherVersion: AttoVersion): AttoVersion = this.max(anotherVersion)

fun UShort.toAttoVersion(): AttoVersion = AttoVersion(this)

fun UInt.toAttoVersion(): AttoVersion = this.toUShort().toAttoVersion()

fun Short.toAttoVersion(): AttoVersion = this.toUShort().toAttoVersion()

fun Int.toAttoVersion(): AttoVersion = this.toUShort().toAttoVersion()

object AttoVersionSerializer : KSerializer<AttoVersion> {
    override val descriptor = UShort.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoVersion,
    ) {
        UShort.serializer().serialize(encoder, value.value)
    }

    override fun deserialize(decoder: Decoder): AttoVersion = AttoVersion(UShort.serializer().deserialize(decoder))
}
