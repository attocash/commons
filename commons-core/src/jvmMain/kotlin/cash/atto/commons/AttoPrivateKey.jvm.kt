package cash.atto.commons

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


private class BIP44(
    val key: ByteArray,
    val secretKeySpec: SecretKeySpec,
) {
    private constructor(derived: ByteArray) : this(
        derived.copyOfRange(0, 32),
        SecretKeySpec(derived, 32, 32, "HmacSHA512"),
    )

    fun derive(value: Int): BIP44 {
        val hmacSha512 = Mac.getInstance("HmacSHA512")
        hmacSha512.init(secretKeySpec)
        hmacSha512.update(0.toByte())

        hmacSha512.update(key, 0, 32)

        val buffer = Buffer()
        buffer.writeInt(value)
        val indexBytes = buffer.readByteArray()
        indexBytes[0] = (indexBytes[0].toInt() or 128.toByte().toInt()).toByte() // hardened

        hmacSha512.update(indexBytes, 0, indexBytes.size)

        val derived = ByteArray(64)
        hmacSha512.doFinal(derived, 0)

        return BIP44(derived)
    }

    companion object {
        fun ed25519(
            seed: AttoSeed,
            path: String,
        ): ByteArray {
            val hmacSha512 = Mac.getInstance("HmacSHA512")

            hmacSha512.init(SecretKeySpec("ed25519 seed".encodeToByteArray(), "HmacSHA512"))
            hmacSha512.update(seed.value, 0, seed.value.size)

            val values =
                path
                    .split("/")
                    .asSequence()
                    .map { it.trim() }
                    .filter { !"M".equals(it, ignoreCase = true) }
                    .map { it.replace("'", "").toInt() }
                    .toList()

            var bip44 = BIP44(hmacSha512.doFinal())
            for (v in values) {
                bip44 = bip44.derive(v)
            }

            return bip44.key
        }
    }
}

actual fun ed25519BIP44(seed: AttoSeed, path: String): ByteArray {
    return BIP44.ed25519(seed, path)
}
