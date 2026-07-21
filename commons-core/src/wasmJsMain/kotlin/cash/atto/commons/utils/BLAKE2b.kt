@file:JsModule("@noble/hashes/blake2.js")
@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.utils

import org.khronos.webgl.Uint8Array

external interface Blake2bHash : JsAny {
    fun update(data: Uint8Array): Blake2bHash

    fun digest(): Uint8Array
}

external interface Blake2bFactory : JsAny {
    fun create(options: JsAny): Blake2bHash
}

external val blake2b: Blake2bFactory
