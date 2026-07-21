@file:JsModule("@noble/hashes/blake2.js")
@file:JsNonModule

package cash.atto.commons.utils

import org.khronos.webgl.Uint8Array

external interface Blake2bHash {
    fun update(data: Uint8Array): Blake2bHash

    fun digest(): Uint8Array
}

external interface Blake2bFactory {
    fun create(options: dynamic): Blake2bHash
}

external val blake2b: Blake2bFactory
