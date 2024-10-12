package cash.atto.commons.utils

expect object Base32 {
    fun encode(data: ByteArray): String
    fun decode(base32: String): ByteArray
}
