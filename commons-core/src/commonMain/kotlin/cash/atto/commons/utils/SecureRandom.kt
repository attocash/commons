package cash.atto.commons.utils

expect object SecureRandom {
    fun randomByteArray(size: UInt): ByteArray
}
