package cash.atto.commons

import java.net.InetAddress
import java.net.InetSocketAddress

data class AttoSocketAddress(
    val address: ByteArray,
    val port: UShort,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttoSocketAddress

        if (!address.contentEquals(other.address)) return false
        if (port != other.port) return false

        return true
    }

    override fun hashCode(): Int {
        var result = address.contentHashCode()
        result = 31 * result + port.hashCode()
        return result
    }
}

fun AttoSocketAddress.toInetSocketAddress(): InetSocketAddress {
    val address = InetAddress.getByAddress(this.address)
    val port = this.port.toInt()

    return InetSocketAddress(address, port)
}

fun InetSocketAddress.toAttoSocketAddress(): AttoSocketAddress {
    val address = this.address.address
    val port = this.port.toUShort()

    val byteArray = ByteArray(16)
    if (address.size == 16) {
        System.arraycopy(address, 0, byteArray, 0, 16)
    } else {
        byteArray[10] = -1
        byteArray[11] = -1
        System.arraycopy(address, 0, byteArray, 12, 4)
    }

    return AttoSocketAddress(address, port)
}
