package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import cash.atto.commons.utils.SecureRandom
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmSynthetic

private fun toEntropyWithChecksum(words: List<String>): ByteArray {
    val buffer = Buffer()

    var scratch = 0
    var offset = 0
    for (word in words) {
        val index = AttoMnemonicDictionary.map[word]!!
        scratch = scratch shl 11
        scratch = scratch or index
        offset += 11
        while (offset >= 8) {
            buffer.writeByte((scratch shr offset - 8).toByte())
            offset -= 8
        }
    }

    if (offset != 0) {
        buffer.writeByte((scratch shl offset).toByte())
    }

    return buffer.readByteArray()
}

internal expect suspend fun checksum(entropy: ByteArray): Byte

@JsExportForJs
@OptIn(ExperimentalJsExport::class)
class AttoMnemonic private constructor(
    words: List<String>,
) {
    @OptIn(ExperimentalJsExport::class)
    @JsExport.Ignore
    val words: List<String> = words.toList()

    val phrase: String
        get() = words.joinToString(" ")

    companion object {
        const val ENTROPY_SIZE = 33

        @JsExport.Ignore
        @JvmSynthetic
        suspend fun fromWords(words: List<String>): AttoMnemonic {
            if (words.size != 24) {
                throw AttoMnemonicException("Mnemonic should have 24 words")
            }

            for (word in words) {
                if (!AttoMnemonicDictionary.set.contains(word)) {
                    throw AttoMnemonicException("The word $word is not part of english mnemonic dictionary")
                }
            }

            val entropyWithChecksum = toEntropyWithChecksum(words)
            if (entropyWithChecksum[32] != checksum(entropyWithChecksum)) {
                throw AttoMnemonicException("Invalid mnemonic")
            }

            return AttoMnemonic(words)
        }

        @OptIn(ExperimentalJsStatic::class)
        @JsName("fromPhrase")
        @JsStatic
        @JvmSynthetic
        suspend fun fromPhrase(phrase: String): AttoMnemonic = fromWords(phrase.split(" "))

        @OptIn(ExperimentalJsStatic::class)
        @JsName("fromEntropy")
        @JsStatic
        @JvmSynthetic
        suspend fun fromEntropy(entropy: ByteArray): AttoMnemonic {
            if (entropy.size != ENTROPY_SIZE) {
                throw AttoMnemonicException("Entropy should have $ENTROPY_SIZE bytes")
            }

            val words = ArrayList<String>(24)
            val copy = entropy.copyOf()
            copy[32] = checksum(entropy)
            var scratch = 0
            var offset = 0
            for (byte in copy) {
                scratch = scratch shl 8
                scratch = scratch or (byte.toInt() and 0xFF)
                offset += 8
                if (offset >= 11) {
                    val index: Int = scratch shr offset - 11 and 0x7FF
                    offset -= 11
                    words.add(AttoMnemonicDictionary.list[index])
                }
            }
            return AttoMnemonic(words)
        }

        @OptIn(ExperimentalJsStatic::class)
        @JsStatic
        @JvmSynthetic
        suspend fun generate(): AttoMnemonic {
            val entropy = SecureRandom.randomByteArray(ENTROPY_SIZE.toUInt())
            return fromEntropy(entropy)
        }
    }

    fun toEntropy(): ByteArray = toEntropyWithChecksum(words).sliceArray(0 until ENTROPY_SIZE)

    @JsName("toSeedAsync")
    @JvmSynthetic
    suspend fun toSeed(passphrase: String = ""): AttoSeed {
        val mnemonic = words.joinToString(" ")
        val salt = "mnemonic$passphrase"
        val iterations = 2048
        val keyLength = 512

        val key = generateSecretWithPBKDF2WithHmacSHA512(mnemonic.toCharArray(), salt.encodeToByteArray(), iterations, keyLength)

        return AttoSeed(key)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttoMnemonic) return false

        if (words != other.words) return false

        return true
    }

    override fun hashCode(): Int = words.hashCode()

    override fun toString(): String = "AttoMnemonic(words=${words.size})"
}

class AttoMnemonicException(
    message: String,
) : RuntimeException(message)
