package cash.atto.commons

expect object AttoHasher {
    fun hash(size: Int, vararg byteArrays: ByteArray): ByteArray
}
