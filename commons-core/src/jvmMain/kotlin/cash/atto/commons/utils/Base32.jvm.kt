package cash.atto.commons.utils

actual object Base32 {
    actual fun encode(data: ByteArray): String {
        return String(org.bouncycastle.util.encoders.Base32.encode(data))
    }

    actual fun decode(base32: String): ByteArray {
        return org.bouncycastle.util.encoders.Base32.decode(base32)
    }
}
