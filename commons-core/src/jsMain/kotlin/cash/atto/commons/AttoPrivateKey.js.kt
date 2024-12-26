package cash.atto.commons

import cash.atto.commons.utils.HMAC
import cash.atto.commons.utils.SHA512Algorithm
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

actual class HmacSha512 actual constructor(secretKey: ByteArray) {
    private val hmac = HMAC(SHA512Algorithm, Uint8Array(secretKey.toTypedArray()))


    actual fun update(data: ByteArray, offset: Int, len: Int) {
        val chunk = data.copyOfRange(offset, offset + len)
        hmac.update(Uint8Array(chunk.toTypedArray()))
    }

    actual fun doFinal(output: ByteArray, offset: Int) {
        val digest = hmac.digest()

        repeat(digest.length) { index ->
            output[offset + index] = digest[index]
        }
    }
}
