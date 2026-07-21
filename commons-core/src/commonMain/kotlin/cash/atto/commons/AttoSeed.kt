package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmSynthetic

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
data class AttoSeed(
    val value: ByteArray,
) {
    init {
        value.checkLength(64)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttoSeed) return false

        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int = value.contentHashCode()

    @JvmSynthetic
    suspend fun toPrivateKey(index: AttoKeyIndex): AttoPrivateKey = toPrivateKey(index.value)

    @JsExport.Ignore
    @JvmSynthetic
    suspend fun toPrivateKey(index: UInt): AttoPrivateKey = derivePrivateKey(this, index)

    @JsExport.Ignore
    @JvmSynthetic
    suspend fun toPrivateKey(index: Int): AttoPrivateKey = toPrivateKey(index.toUInt())

    @JsExport.Ignore
    @JvmSynthetic
    suspend fun toSigner(index: AttoKeyIndex): AttoSigner = toPrivateKey(index).toSigner()

    @JsExport.Ignore
    @JvmSynthetic
    suspend fun toSigner(index: UInt): AttoSigner = toPrivateKey(index).toSigner()

    @JsExport.Ignore
    @JvmSynthetic
    suspend fun toSigner(index: Int): AttoSigner = toPrivateKey(index).toSigner()

    override fun toString(): String = "AttoSeed(value='${value.size} bytes')"
}

internal expect suspend fun generateSecretWithPBKDF2WithHmacSHA512(
    mnemonic: CharArray,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int,
): ByteArray

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
@JsName("toSeedAsync")
@JvmSynthetic
@Deprecated(
    "Moved to AttoMnemonic.toSeed(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.toSeed(passphrase)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun AttoMnemonic.toSeed(passphrase: String = ""): AttoSeed = this.toSeed(passphrase)
