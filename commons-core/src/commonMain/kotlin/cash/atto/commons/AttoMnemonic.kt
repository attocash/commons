package cash.atto.commons

import cash.atto.commons.utils.SecureRandom
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.js.JsName

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

internal expect fun checksum(entropy: ByteArray): Byte

class AttoMnemonic {
    val words: List<String>

    @JsName("fromWords")
    constructor(words: List<String>) {
        if (words.size != 24) {
            throw AttoMnemonicException("Mnemonic should have 24 words")
        }

        for (word in words) {
            if (!AttoMnemonicDictionary.set.contains(word)) {
                throw AttoMnemonicException("The word $word is not part of english mnemonic dictionary")
            }
        }

        val entropyWithChecksum = toEntropyWithChecksum(words)
        val checksum = checksum(entropyWithChecksum)

        if (entropyWithChecksum[32] != checksum) {
            throw AttoMnemonicException("Invalid mnemonic")
        }

        this.words = words.toList()
    }

    @JsName("fromString")
    constructor(words: String) : this(words.split(" "))

    @JsName("fromEntropy")
    constructor(entropy: ByteArray) {
        val words = ArrayList<String>(24)

        val copy = entropy.copyOf()
        copy[32] = checksum(entropy)
        var scratch = 0
        var offset = 0
        for (b in copy) {
            scratch = scratch shl 8
            scratch = scratch or (b.toInt() and 0xFF)
            offset += 8
            if (offset >= 11) {
                val index: Int = scratch shr offset - 11 and 0x7FF
                offset -= 11
                words.add(AttoMnemonicDictionary.list[index])
            }
        }
        this.words = words.toList()
    }

    companion object {
        fun generate(): AttoMnemonic {
            val entropy = SecureRandom.randomByteArray(33U)
            return AttoMnemonic(entropy)
        }
    }

    fun toEntropy(): ByteArray = toEntropyWithChecksum(words).sliceArray(0 until 33)

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
