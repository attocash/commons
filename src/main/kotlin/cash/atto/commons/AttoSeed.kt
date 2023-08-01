package cash.atto.commons

import java.nio.charset.StandardCharsets
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class AttoSeed(val value: ByteArray) {
    init {
        value.checkLength(64)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttoSeed) return false

        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    override fun toString(): String {
        return "${this.javaClass.simpleName}(value='${value.size} bytes')"
    }
}

/**
 * BIP39 seed generation
 */
fun AttoMnemonic.toSeed(passphrase: String = ""): AttoSeed {
    val mnemonic = words.joinToString(" ")
    val salt = "mnemonic$passphrase"
    val iterations = 2048
    val keyLength = 512

    val spec = PBEKeySpec(mnemonic.toCharArray(), salt.toByteArray(StandardCharsets.UTF_8), iterations, keyLength)

    val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
    val key = skf.generateSecret(spec).encoded

    return AttoSeed(key)
}
