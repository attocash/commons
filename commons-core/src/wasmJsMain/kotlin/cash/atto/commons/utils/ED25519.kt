@file:JsModule("@stablelib/ed25519")
@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.utils

import org.khronos.webgl.Uint8Array

external interface KeyPair {
    val publicKey: Uint8Array
    val secretKey: Uint8Array
}

external fun generateKeyPairFromSeed(seed: Uint8Array): KeyPair

external fun sign(
    secretKey: Uint8Array,
    message: Uint8Array,
): Uint8Array

external fun verify(
    publicKey: Uint8Array,
    message: Uint8Array,
    signature: Uint8Array,
): Boolean
