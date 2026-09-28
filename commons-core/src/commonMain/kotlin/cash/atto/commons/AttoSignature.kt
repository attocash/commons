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
import kotlin.js.JsName
import kotlin.jvm.JvmSynthetic

// All eight canonical encodings in the Ed25519 torsion subgroup; this is not a full point validity check.
private val smallOrderEd25519PointEncodings =
    setOf(
        "00".repeat(32),
        "00".repeat(31) + "80",
        "01" + "00".repeat(31),
        "EC" + "FF".repeat(30) + "7F",
        "C7176A703D4DD84FBA3C0B760D10670F2A2053FA2C39CCC64EC7FD7792AC037A",
        "C7176A703D4DD84FBA3C0B760D10670F2A2053FA2C39CCC64EC7FD7792AC03FA",
        "26E8958FC2B227B045C3F489F2EF98F0D5DFAC05D3C63339B13802886D53FC05",
        "26E8958FC2B227B045C3F489F2EF98F0D5DFAC05D3C63339B13802886D53FC85",
    )

private val ed25519GroupOrder = "EDD3F55C1A631258D69CF7A2DEF9DE14" + "00".repeat(15) + "10"

private fun ByteArray.isCanonicalEd25519PointEncoding(): Boolean {
    if (size != 32) return false
    if ((this[31].toUByte().toInt() and 0x7F) < 0x7F) return true
    for (index in 30 downTo 1) {
        if (this[index].toUByte().toInt() < 0xFF) return true
    }
    return this[0].toUByte().toInt() < 0xED
}

private fun ByteArray.isCanonicalEd25519Scalar(): Boolean {
    if (size != 32) return false
    for (index in 31 downTo 0) {
        val value = this[index].toUByte().toInt()
        val limit = ed25519GroupOrder.substring(index * 2, index * 2 + 2).toInt(16)
        if (value < limit) return true
        if (value > limit) return false
    }
    return false
}

internal fun ByteArray.passesEd25519PointEncodingPrechecks(): Boolean =
    isCanonicalEd25519PointEncoding() && toHex() !in smallOrderEd25519PointEncodings

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

    internal fun passesVerificationPrechecks(): Boolean =
        value.copyOfRange(0, 32).passesEd25519PointEncodingPrechecks() &&
            value.copyOfRange(32, 64).isCanonicalEd25519Scalar()

    @JsExport.Ignore
    @JvmSynthetic
    suspend fun isValid(
        publicKey: AttoPublicKey,
        hash: AttoHash,
    ): Boolean {
        if (!publicKey.value.passesEd25519PointEncodingPrechecks() || !passesVerificationPrechecks()) return false
        return try {
            verifyEd25519(this, publicKey, hash)
        } catch (_: IllegalArgumentException) {
            // The JVM verifier throws for malformed public key points.
            false
        }
    }

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

    /**
     * Verify exact message bytes using the ATTO Signed Message v1 framing.
     * Keys and signatures also hold unverified wire data; the shared verifier prechecks their encodings.
     */
    @JsName("isValidMessage")
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
