@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cash.atto.commons

import cash.atto.commons.utils.blake2b

actual object AttoHasher {
    actual fun hash(
        size: Int,
        vararg byteArrays: ByteArray,
    ): ByteArray {
        val hasher = blake2b.create(blake2bOptions(size))

        for (byteArray in byteArrays) {
            hasher.update(byteArray.toUint8Array())
        }

        return hasher.digest().toByteArray()
    }
}

private fun blake2bOptions(size: Int): JsAny = js("""({ "dkLen": size })""")
