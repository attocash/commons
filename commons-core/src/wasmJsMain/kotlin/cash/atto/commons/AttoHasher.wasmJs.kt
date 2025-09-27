package cash.atto.commons

import cash.atto.commons.utils.BLAKE2b

actual object AttoHasher {
    actual fun hash(
        size: Int,
        vararg byteArrays: ByteArray,
    ): ByteArray {
        @OptIn(ExperimentalWasmJsInterop::class)
        val hasher = BLAKE2b(size)

        for (byteArray in byteArrays) {
            hasher.update(byteArray.toUint8Array())
        }

        return hasher.digest().toByteArray()
    }
}
