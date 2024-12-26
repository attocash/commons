package cash.atto.commons.utils

import cash.atto.commons.toByteArray
import org.khronos.webgl.Uint8Array

actual object SecureRandom {
    actual fun randomByteArray(size: UInt): ByteArray {
        val uint8Array = Uint8Array(size.toInt())

        js("crypto.getRandomValues")(uint8Array)

        return uint8Array.toByteArray()
    }
}
