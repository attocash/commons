package cash.atto.commons

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual class HmacSha512 actual constructor(secretKey: ByteArray, algorithm: String) {
    private val mac: Mac = Mac.getInstance(algorithm).apply {
        init(SecretKeySpec(secretKey, algorithm))
    }

    actual fun update(data: ByteArray, offset: Int, len: Int) {
        mac.update(data, offset, len)
    }

    actual fun doFinal(output: ByteArray, offset: Int) {
        mac.doFinal(output, offset)
    }
}
