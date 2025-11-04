package cash.atto.commons

import cash.atto.commons.utils.HMAC
import cash.atto.commons.utils.SHA512Algorithm
import org.khronos.webgl.get

internal object AttoPrivateKeyHolder

internal actual class HmacSha512 actual constructor(
    secretKey: ByteArray,
) {
    @OptIn(ExperimentalWasmJsInterop::class)
    private val hmac = HMAC(SHA512Algorithm, secretKey.toUint8Array())

    actual fun update(
        data: ByteArray,
        offset: Int,
        len: Int,
    ) {
        val chunk = data.copyOfRange(offset, offset + len)
        hmac.update(chunk.toUint8Array())
    }

    actual fun doFinal(
        output: ByteArray,
        offset: Int,
    ) {
        val digest = hmac.digest()

        repeat(digest.length) { index ->
            output[offset + index] = digest[index]
        }
    }
}
