package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.ExperimentalJsExport
import kotlin.jvm.JvmSynthetic

@OptIn(ExperimentalJsExport::class)
@Serializable(with = AttoPublicKeyAsStringSerializer::class)
@JsExportForJs
data class AttoPublicKey(
    val value: ByteArray,
) {
    init {
        value.checkLength(32)
    }

    companion object {
        fun parse(value: String): AttoPublicKey = AttoPublicKey(value.fromHexToByteArray(32))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttoPublicKey) return false

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()

    fun toAddress(algorithm: AttoAlgorithm): AttoAddress = AttoAddress(algorithm, this)

    override fun toString(): String = value.toHex()
}

@JsExportForJs
@JvmSynthetic
@Deprecated(
    "Moved to AttoPrivateKey.toPublicKey(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.toPublicKey()"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun AttoPrivateKey.toPublicKey(): AttoPublicKey = this.toPublicKey()

object AttoPublicKeyAsStringSerializer : KSerializer<AttoPublicKey> {
    override val descriptor = PrimitiveSerialDescriptor("AttoPublicKeyAsString", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoPublicKey,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoPublicKey = AttoPublicKey.parse(decoder.decodeString())
}

object AttoPublicKeyAsByteArraySerializer : KSerializer<AttoPublicKey> {
    override val descriptor = PrimitiveSerialDescriptor("AttoPublicKeyAsByteArray", PrimitiveKind.BYTE)

    override fun serialize(
        encoder: Encoder,
        value: AttoPublicKey,
    ) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.value)
    }

    override fun deserialize(decoder: Decoder): AttoPublicKey = AttoPublicKey(decoder.decodeSerializableValue(ByteArraySerializer()))
}
