package cash.atto.commons.utils

actual object SecureRandom {
    val random = java.security.SecureRandom.getInstanceStrong()

    actual fun randomByteArray(size: UInt): ByteArray {
        val value = ByteArray(size.toInt())
        random.nextBytes(value)
        return value
    }
}
