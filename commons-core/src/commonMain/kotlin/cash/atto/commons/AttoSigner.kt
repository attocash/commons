package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlin.js.JsName
import kotlin.jvm.JvmSynthetic

@JsExportForJs
interface AttoSigner {
    companion object {}

    val algorithm: AttoAlgorithm
    val publicKey: AttoPublicKey
    val address: AttoAddress

    @JsName("signHash")
    @JvmSynthetic
    suspend fun sign(hash: AttoHash): AttoSignature

    @JsName("signBlock")
    @JvmSynthetic
    suspend fun sign(block: AttoBlock): AttoSignature {
        checkPublicKey(block.publicKey)
        return sign(block.hash)
    }

    @JsName("signVote")
    @JvmSynthetic
    suspend fun sign(vote: AttoVote): AttoSignature {
        checkPublicKey(vote.publicKey)
        return sign(vote.hash)
    }

    @JsName("signChallenge")
    @JvmSynthetic
    suspend fun sign(
        challenge: AttoChallenge,
        timestamp: AttoInstant,
    ): AttoSignature = sign(AttoHash.hash(64, publicKey.value, challenge.value, timestamp.toByteArray()))

    @JsName("signMessage")
    @JvmSynthetic
    suspend fun signMessage(message: ByteArray): AttoSignature = sign(attoSignedMessageHash(publicKey, message))

    @JvmSynthetic
    suspend fun checkPublicKey(publicKey: AttoPublicKey) {
        if (this.publicKey != publicKey) {
            throw IllegalArgumentException("Different public key ${this.publicKey}")
        }
    }
}

internal expect class Ed25519SigningKey {
    val publicKey: AttoPublicKey

    suspend fun sign(hash: AttoHash): AttoSignature
}

internal expect suspend fun loadEd25519SigningKey(privateKey: AttoPrivateKey): Ed25519SigningKey

class InMemorySigner internal constructor(
    internal val privateKey: AttoPrivateKey,
    private val signingKey: Ed25519SigningKey,
) : AttoSigner {
    override val algorithm: AttoAlgorithm = AttoAlgorithm.V1
    override val publicKey: AttoPublicKey = signingKey.publicKey
    override val address: AttoAddress = publicKey.toAddress(algorithm)

    @JvmSynthetic
    override suspend fun sign(hash: AttoHash): AttoSignature = signingKey.sign(hash)
}

@JsExportForJs
@JsName("privateKeyToSigner")
@JvmSynthetic
@Deprecated(
    "Moved to AttoPrivateKey.toSigner(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.toSigner()"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun AttoPrivateKey.toSigner(): AttoSigner = this.toSigner()

@JvmSynthetic
@Deprecated(
    "Moved to AttoSeed.toSigner(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.toSigner(index)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun AttoSeed.toSigner(index: AttoKeyIndex): AttoSigner = this.toSigner(index)

@JvmSynthetic
@Deprecated(
    "Moved to AttoSeed.toSigner(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.toSigner(index)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun AttoSeed.toSigner(index: UInt): AttoSigner = this.toSigner(index)

@JvmSynthetic
@Deprecated(
    "Moved to AttoSeed.toSigner(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.toSigner(index)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun AttoSeed.toSigner(index: Int): AttoSigner = this.toSigner(index)

@Deprecated(
    "Moved to AttoPrivateKey.sign(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.sign(hash)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
@JvmSynthetic
suspend fun AttoPrivateKey.sign(hash: AttoHash): AttoSignature = this.sign(hash)

@Deprecated(
    "Moved to AttoPrivateKey.signMessage(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.signMessage(message)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
@JvmSynthetic
suspend fun AttoPrivateKey.signMessage(message: ByteArray): AttoSignature = this.signMessage(message)

internal fun String.base64UrlToByteArray(): ByteArray {
    val output = ByteArray(length * 6 / 8)
    var outputIndex = 0
    var accumulator = 0
    var bits = 0

    for (char in this) {
        val value = BASE64_URL_ALPHABET.indexOf(char)
        require(value >= 0) { "Invalid Base64URL character '$char'" }

        accumulator = (accumulator shl 6) or value
        bits += 6
        if (bits >= 8) {
            bits -= 8
            output[outputIndex++] = (accumulator shr bits).toByte()
            accumulator = if (bits == 0) 0 else accumulator and ((1 shl bits) - 1)
        }
    }

    return output.copyOf(outputIndex)
}

internal val ED25519_PKCS8_PREFIX = "302E020100300506032B657004220420".fromHexToByteArray()
private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
