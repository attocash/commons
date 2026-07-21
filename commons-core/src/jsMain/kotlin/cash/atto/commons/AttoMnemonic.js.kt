package cash.atto.commons

import cash.atto.commons.utils.getSubtleCryptoInstance
import org.khronos.webgl.Uint8Array

internal actual suspend fun checksum(entropy: ByteArray): Byte {
    val digest =
        getSubtleCryptoInstance()
            .digest("SHA-256", entropy.copyOfRange(0, 32).toUint8Array())
            .await()

    return Uint8Array(digest).toByteArray()[0]
}
