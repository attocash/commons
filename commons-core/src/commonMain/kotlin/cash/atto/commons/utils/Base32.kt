package cash.atto.commons.utils

object Base32 {
    private val ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"
    private val DECODE_MAP =
        IntArray(128) { -1 }.apply {
            ALPHABET.forEachIndexed { index, char -> this[char.code] = index }
            ALPHABET.uppercase().forEachIndexed { index, char -> this[char.code] = index }
        }

    fun encode(data: ByteArray): String {
        val result = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        data.forEach { byte ->
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                result.append(ALPHABET[(buffer shr (bitsLeft - 5)) and 0x1F])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            result.append(ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        while (result.length % 8 != 0) {
            result.append("=")
        }
        return result.toString()
    }

    fun decode(base32: String): ByteArray {
        val data = base32.filter { it != '=' }.lowercase()
        val output = ByteArray((data.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var index = 0
        data.forEach { char ->
            val value = DECODE_MAP[char.code]
            require(value != -1) { "Invalid Base32 character: $char" }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output[index++] = (buffer shr (bitsLeft - 8)).toByte()
                bitsLeft -= 8
            }
        }
        return output
    }
}
