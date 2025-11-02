package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import cash.atto.commons.utils.SecureRandom
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
class AttoPrivateKey(
    val value: ByteArray,
) {
    init {
        value.checkLength(32)
    }

    @JsExport.Ignore
    constructor(seed: AttoSeed, index: UInt) : this(ed25519BIP44(seed, "m/44'/$coinType'/$index'"))

    @JsExport.Ignore
    constructor(seed: AttoSeed, index: AttoKeyIndex) : this(seed, index.value)

    companion object {
        private val coinType = 1869902945 // "atto".toByteArray().toUInt()

        fun parse(value: String): AttoPrivateKey = AttoPrivateKey(value.fromHexToByteArray())

        fun generate(): AttoPrivateKey {
            val value = SecureRandom.randomByteArray(32U)
            return AttoPrivateKey(value)
        }
    }

    override fun toString(): String = "${value.size} bytes"
}

fun AttoSeed.toPrivateKey(index: AttoKeyIndex): AttoPrivateKey = AttoPrivateKey(this, index)

fun AttoSeed.toPrivateKey(index: UInt): AttoPrivateKey = AttoPrivateKey(this, index)

fun AttoSeed.toPrivateKey(index: Int): AttoPrivateKey = AttoPrivateKey(this, index.toUInt())

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
            val algorithm = AttoAlgorithm.from(byteArray[0].toUByte())
            val privateKey = AttoPrivateKey(byteArray.sliceArray(1 until byteArray.size))
            return AttoAlgorithmPrivateKey(algorithm, privateKey)
        }
    }

    override fun toString(): String = "${value.size} bytes"
}

expect class HmacSha512(
    secretKey: ByteArray,
) {
    fun update(
        data: ByteArray,
        offset: Int = 0,
        len: Int = data.size,
    )

    fun doFinal(
        output: ByteArray,
        offset: Int = 0,
    )
}

private class BIP44(
    val key: ByteArray,
    val hmacHelper: HmacSha512,
) {
    private constructor(derived: ByteArray) : this(
        derived.copyOfRange(0, 32),
        HmacSha512(derived.copyOfRange(32, 64)),
    )

    fun derive(value: Int): BIP44 {
        hmacHelper.update(byteArrayOf(0))
        hmacHelper.update(key, 0, 32)

        val buffer = Buffer()
        buffer.writeInt(value)
        val indexBytes = buffer.readByteArray()
        indexBytes[0] = (indexBytes[0].toInt() or 128.toByte().toInt()).toByte() // hardened

        hmacHelper.update(indexBytes, 0, indexBytes.size)

        val derived = ByteArray(64)
        hmacHelper.doFinal(derived, 0)

        return BIP44(derived)
    }

    companion object {
        fun ed25519(
            seed: AttoSeed,
            path: String,
        ): ByteArray {
            val hmacHelper = HmacSha512("ed25519 seed".encodeToByteArray())
            hmacHelper.update(seed.value)

            val values =
                path
                    .split("/")
                    .asSequence()
                    .map { it.trim() }
                    .filter { !"M".equals(it, ignoreCase = true) }
                    .map { it.replace("'", "").toInt() }
                    .toList()

            var bip44 = BIP44(ByteArray(64).also { hmacHelper.doFinal(it, 0) })
            for (v in values) {
                bip44 = bip44.derive(v)
            }

            return bip44.key
        }
    }
}

private fun ed25519BIP44(
    seed: AttoSeed,
    path: String,
): ByteArray = BIP44.ed25519(seed, path)
