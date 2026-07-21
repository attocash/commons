package cash.atto.commons

import cash.atto.commons.utils.blake2b
import kotlin.js.json

actual object AttoHasher {
    actual fun hash(
        size: Int,
        vararg byteArrays: ByteArray,
    ): ByteArray {
        val hasher = blake2b.create(json("dkLen" to size))

        for (byteArray in byteArrays) {
            hasher.update(byteArray.toUint8Array())
        }

        return hasher.digest().toByteArray()
    }
}
