package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import cash.atto.commons.utils.SecureRandom
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmSynthetic

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
class AttoPrivateKey(
    val value: ByteArray,
) {
    init {
        value.checkLength(32)
    }

    companion object {
        fun parse(value: String): AttoPrivateKey = AttoPrivateKey(value.fromHexToByteArray(32))

        fun generate(): AttoPrivateKey {
            val value = SecureRandom.randomByteArray(32U)
            return AttoPrivateKey(value)
        }
    }

    @JvmSynthetic
    suspend fun toPublicKey(): AttoPublicKey = loadEd25519SigningKey(this).publicKey

    @JvmSynthetic
    suspend fun toSigner(): AttoSigner = InMemorySigner(this, loadEd25519SigningKey(this))

    @JsExport.Ignore
    @JvmSynthetic
    suspend fun sign(hash: AttoHash): AttoSignature = toSigner().sign(hash)

    @JsExport.Ignore
    @JvmSynthetic
    suspend fun signMessage(message: ByteArray): AttoSignature = toSigner().signMessage(message)

    internal fun toEd25519Pkcs8(): ByteArray = ED25519_PKCS8_PREFIX + value

    override fun toString(): String = "${value.size} bytes"
}

@JsExportForJs
@JvmSynthetic
@Deprecated(
    "Moved to AttoSeed.toPrivateKey(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.toPrivateKey(index)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun AttoSeed.toPrivateKey(index: AttoKeyIndex): AttoPrivateKey = this.toPrivateKey(index)

@JvmSynthetic
@Deprecated(
    "Moved to AttoSeed.toPrivateKey(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.toPrivateKey(index)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun AttoSeed.toPrivateKey(index: UInt): AttoPrivateKey = this.toPrivateKey(index)

@JvmSynthetic
@Deprecated(
    "Moved to AttoSeed.toPrivateKey(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.toPrivateKey(index)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun AttoSeed.toPrivateKey(index: Int): AttoPrivateKey = this.toPrivateKey(index)

class AttoAlgorithmPrivateKey(
    val algorithm: AttoAlgorithm,
    val privateKey: AttoPrivateKey,
) {
    val value = byteArrayOf(algorithm.code.toByte()) + privateKey.value

    init {
        privateKey.value.checkLength(algorithm.privateKeySize)
    }

    companion object {
        fun parse(value: String): AttoAlgorithmPrivateKey {
            val byteArray = value.fromHexToByteArray()
            require(byteArray.isNotEmpty()) { "Hex string is empty" }
            val algorithm = AttoAlgorithm.from(byteArray[0].toUByte())
            require(byteArray.size == 1 + algorithm.privateKeySize) {
                "Hex string contains ${value.length} characters but should contain ${(1 + algorithm.privateKeySize) * 2}"
            }
            val privateKey = AttoPrivateKey(byteArray.sliceArray(1 until byteArray.size))
            return AttoAlgorithmPrivateKey(algorithm, privateKey)
        }
    }

    override fun toString(): String = "${value.size} bytes"
}

internal expect suspend fun hmacSha512(
    secretKey: ByteArray,
    data: ByteArray,
): ByteArray

private class BIP44(
    val key: ByteArray,
    val chainCode: ByteArray,
) {
    private constructor(derived: ByteArray) : this(
        derived.copyOfRange(0, 32),
        derived.copyOfRange(32, 64),
    )

    suspend fun derive(value: Int): BIP44 {
        val buffer = Buffer()
        buffer.writeInt(value)
        val indexBytes = buffer.readByteArray()
        indexBytes[0] = (indexBytes[0].toInt() or 128.toByte().toInt()).toByte() // hardened

        val derived = hmacSha512(chainCode, byteArrayOf(0) + key + indexBytes)
        return BIP44(derived)
    }

    companion object {
        suspend fun ed25519(
            seed: AttoSeed,
            path: String,
        ): ByteArray {
            val values =
                path
                    .split("/")
                    .asSequence()
                    .map { it.trim() }
                    .filter { !"M".equals(it, ignoreCase = true) }
                    .map { it.replace("'", "").toInt() }
                    .toList()

            var bip44 = BIP44(hmacSha512("ed25519 seed".encodeToByteArray(), seed.value))
            for (v in values) {
                bip44 = bip44.derive(v)
            }

            return bip44.key
        }
    }
}

internal suspend fun derivePrivateKey(
    seed: AttoSeed,
    index: UInt,
): AttoPrivateKey = AttoPrivateKey(BIP44.ed25519(seed, "m/44'/$COIN_TYPE'/$index'"))

private const val COIN_TYPE = 1869902945 // "atto".toByteArray().toUInt()
