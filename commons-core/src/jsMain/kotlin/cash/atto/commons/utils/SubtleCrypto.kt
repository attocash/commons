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
        // ── Are we running under Node? ────────────────────────────────
        var isNode =
            typeof process !== "undefined" &&
            process.versions && process.versions.node;

        if (isNode) {
            // Node ≥20 already exposes a global WebCrypto.
            if (globalThis.crypto && globalThis.crypto.subtle) {
                return globalThis.crypto.subtle;
            }

            // Older Node in Common-JS mode: use plain require.
            if (typeof require === "function") {
                return require("node:crypto").webcrypto.subtle;
            }

            /*  Older Node in ES-module mode: no reliable *sync* way to
                reach `createRequire` without `module`.  Simply fall back
                to browser path – most test runners (vitest, jest-js-dom,
                karma) inject a `window.crypto.subtle` shim anyway.      */
        }

        // ── Browser / worker fallback ─────────────────────────────────
        var root =
            typeof window !== "undefined"  ? window :
            typeof self   !== "undefined"  ? self   :
            {};              // very old JS engines

        var cryptoObj = root.crypto ? root.crypto : root.msCrypto;
        if (!cryptoObj || !cryptoObj.subtle) {
            throw new Error("WebCrypto SubtleCrypto API not available");
        }
        return cryptoObj.subtle;
        """,
    ).unsafeCast<SubtleCrypto>()

external interface CryptoKey
