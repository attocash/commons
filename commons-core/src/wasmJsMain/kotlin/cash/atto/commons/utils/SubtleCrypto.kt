package cash.atto.commons.utils

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

external interface SubtleCrypto {
    fun importKey(
        format: String,
        keyData: Uint8Array,
        algorithm: JsAny,
        extractable: Boolean,
        keyUsages: JsArray<JsString>,
    ): Promise<CryptoKey>

    fun deriveBits(
        algorithm: JsAny,
        baseKey: CryptoKey,
        length: Int,
    ): Promise<ArrayBuffer>

    fun sign(
        algorithm: JsAny,
        key: CryptoKey,
        data: Uint8Array,
    ): Promise<ArrayBuffer>
}

fun getSubtleCryptoInstance(): SubtleCrypto {
    js(
        """
            var isNodeJs = typeof process !== 'undefined' && process.versions != null && process.versions.node != null
            if (isNodeJs) {
                return (eval('require')('node:crypto').webcrypto).subtle;
            } else {
                return (window ? (window.crypto ? window.crypto : window.msCrypto) : self.crypto).subtle;
            }
            """,
    )
}

external interface CryptoKey : JsAny
