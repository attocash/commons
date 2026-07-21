@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.utils

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

external interface SubtleCrypto {
    fun digest(
        algorithm: JsAny,
        data: Uint8Array,
    ): Promise<ArrayBuffer>

    fun importKey(
        format: String,
        keyData: Uint8Array,
        algorithm: JsAny,
        extractable: Boolean,
        keyUsages: JsArray<JsString>,
    ): Promise<CryptoKey>

    fun exportKey(
        format: String,
        key: CryptoKey,
    ): Promise<JsonWebKey>

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

    fun verify(
        algorithm: JsAny,
        key: CryptoKey,
        signature: Uint8Array,
        data: Uint8Array,
    ): Promise<JsBoolean>
}

fun getSubtleCryptoInstance(): SubtleCrypto {
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
    )
}

external interface CryptoKey : JsAny

external interface JsonWebKey : JsAny {
    val x: JsString
}

fun mapKeyUsages(vararg keyUsages: String): JsArray<JsString> =
    JsArray<JsString>().also {
        keyUsages.forEachIndexed { index, value ->
            it[index] = value.toJsString()
        }
    }
