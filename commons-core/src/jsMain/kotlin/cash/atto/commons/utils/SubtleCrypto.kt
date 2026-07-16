package cash.atto.commons.utils

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

external interface SubtleCrypto {
    fun importKey(
        format: String,
        keyData: Uint8Array,
        algorithm: dynamic,
        extractable: Boolean,
        keyUsages: Array<String>,
    ): Promise<CryptoKey>

    fun deriveBits(
        algorithm: dynamic,
        baseKey: CryptoKey,
        length: Int,
    ): Promise<ArrayBuffer>

    fun sign(
        algorithm: dynamic,
        key: CryptoKey,
        data: Uint8Array,
    ): Promise<ArrayBuffer>
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
fun getSubtleCryptoInstance(): SubtleCrypto =
    js(
        """
        if (typeof crypto !== "undefined" && crypto.subtle) {
            return crypto.subtle;
        }

        if (typeof require === "function") {
            var nodeCrypto = require("node:crypto").webcrypto;
            if (nodeCrypto && nodeCrypto.subtle) {
                return nodeCrypto.subtle;
            }
        }

        throw new Error("WebCrypto SubtleCrypto API not available");
        """,
    ).unsafeCast<SubtleCrypto>()

external interface CryptoKey
