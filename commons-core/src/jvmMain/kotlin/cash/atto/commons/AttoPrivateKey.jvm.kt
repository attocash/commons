package cash.atto.commons

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object AttoPrivateKeyHolder

internal actual class HmacSha512 actual constructor(
    secretKey: ByteArray,
) {
    private val mac: Mac =
        Mac.getInstance("HmacSHA512").apply {
            init(SecretKeySpec(secretKey, algorithm))
        }

    actual fun update(
        data: ByteArray,
        offset: Int,
        len: Int,
    ) {
        mac.update(data, offset, len)
    }

    actual fun doFinal(
        output: ByteArray,
        offset: Int,
    ) {
        mac.doFinal(output, offset)
    }
}
