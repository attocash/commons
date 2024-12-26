package cash.atto.commons.utils


import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

external interface SubtleCrypto {
    companion object {}

    fun importKey(
        format: String,
        keyData: Uint8Array,
        algorithm: dynamic,
        extractable: Boolean,
        keyUsages: Array<String>
    ): Promise<CryptoKey>

    fun deriveBits(
        algorithm: dynamic,
        baseKey: CryptoKey,
        length: Int
    ): Promise<ArrayBuffer>

    fun sign(
        algorithm: dynamic,
        key: CryptoKey,
        data: Uint8Array
    ): Promise<ArrayBuffer>
}

fun SubtleCrypto.Companion.getInstance(): SubtleCrypto {
    return js(
        code = """

        var isNodeJs = typeof process !== 'undefined' && process.versions != null && process.versions.node != null
        if (isNodeJs) {
            return (eval('require')('node:crypto').webcrypto).subtle;
        } else {
            return (window ? (window.crypto ? window.crypto : window.msCrypto) : self.crypto).subtle;
        }

               """
    ).unsafeCast<SubtleCrypto>()
}

external interface Algorithm

fun Algorithm(name: String): Algorithm =
    js("{ name: name }").unsafeCast<Algorithm>()

interface CryptoKey
