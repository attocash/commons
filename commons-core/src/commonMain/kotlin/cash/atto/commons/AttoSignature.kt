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
import kotlin.js.JsExport
import kotlin.jvm.JvmSynthetic

@Serializable(with = AttoSignatureAsStringSerializer::class)
@OptIn(ExperimentalJsExport::class)
@JsExportForJs
data class AttoSignature(
    val value: ByteArray,
) {
    companion object {
        const val SIZE = 64

        fun parse(value: String): AttoSignature = AttoSignature(value.fromHexToByteArray(SIZE))
    }

    init {
        value.checkLength(SIZE)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttoSignature) return false

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()

    @JsExport.Ignore
    @JvmSynthetic
    suspend fun isValid(
        publicKey: AttoPublicKey,
        hash: AttoHash,
    ): Boolean = verifyEd25519(this, publicKey, hash)

    @JsExport.Ignore
    @JvmSynthetic
    suspend fun isValid(
        publicKey: AttoPublicKey,
        challenge: AttoChallenge,
        timestamp: AttoInstant,
    ): Boolean {
        val hash = AttoHash.hash(64, publicKey.value, challenge.value, timestamp.toByteArray())
        return isValid(publicKey, hash)
    }

    @JsExport.Ignore
    @JvmSynthetic
    suspend fun isValidMessage(
        publicKey: AttoPublicKey,
        message: ByteArray,
    ): Boolean = isValid(publicKey, attoSignedMessageHash(publicKey, message))

    override fun toString(): String = value.toHex()
}

internal expect suspend fun verifyEd25519(
    signature: AttoSignature,
    publicKey: AttoPublicKey,
    hash: AttoHash,
): Boolean

@JvmSynthetic
@Deprecated(
    "Moved to AttoSignature.isValid(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.isValid(publicKey, hash)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun AttoSignature.isValid(
    publicKey: AttoPublicKey,
    hash: AttoHash,
): Boolean = this.isValid(publicKey, hash)

@JvmSynthetic
@Deprecated(
    "Moved to AttoSignature.isValid(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.isValid(publicKey, challenge, timestamp)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun AttoSignature.isValid(
    publicKey: AttoPublicKey,
    challenge: AttoChallenge,
    timestamp: AttoInstant,
): Boolean = this.isValid(publicKey, challenge, timestamp)

@JvmSynthetic
@Deprecated(
    "Moved to AttoSignature.isValidMessage(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.isValidMessage(publicKey, message)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun AttoSignature.isValidMessage(
    publicKey: AttoPublicKey,
    message: ByteArray,
): Boolean = this.isValidMessage(publicKey, message)

private val ATTO_SIGNED_MESSAGE_DOMAIN = "ATTO Signed Message v1".encodeToByteArray()

internal fun attoSignedMessageHash(
    publicKey: AttoPublicKey,
    message: ByteArray,
): AttoHash =
    AttoHash.hash(
        64,
        ATTO_SIGNED_MESSAGE_DOMAIN,
        publicKey.value,
        message.size.toULong().toByteArray(),
        message,
    )

object AttoSignatureAsStringSerializer : KSerializer<AttoSignature> {
    override val descriptor = PrimitiveSerialDescriptor("AttoSignatureAsString", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoSignature,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoSignature = AttoSignature.parse(decoder.decodeString())
}

object AttoSignatureAsByteArraySerializer : KSerializer<AttoSignature> {
    override val descriptor = PrimitiveSerialDescriptor("AttoSignatureAsByteArray", PrimitiveKind.BYTE)

    override fun serialize(
        encoder: Encoder,
        value: AttoSignature,
    ) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.value)
    }

    override fun deserialize(decoder: Decoder): AttoSignature = AttoSignature(decoder.decodeSerializableValue(ByteArraySerializer()))
}
