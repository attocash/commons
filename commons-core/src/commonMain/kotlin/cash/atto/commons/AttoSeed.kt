package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlin.js.ExperimentalJsExport
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

    override fun toString(): String = "AttoSeed(value='${value.size} bytes')"
}

internal expect suspend fun generateSecretWithPBKDF2WithHmacSHA512(
    mnemonic: CharArray,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int,
): ByteArray

@JvmSynthetic
suspend fun AttoMnemonic.toSeed(passphrase: String = ""): AttoSeed {
    val mnemonic = words.joinToString(" ")
    val salt = "mnemonic$passphrase"
    val iterations = 2048
    val keyLength = 512

    val key = generateSecretWithPBKDF2WithHmacSHA512(mnemonic.toCharArray(), salt.encodeToByteArray(), iterations, keyLength)

    return AttoSeed(key)
}
