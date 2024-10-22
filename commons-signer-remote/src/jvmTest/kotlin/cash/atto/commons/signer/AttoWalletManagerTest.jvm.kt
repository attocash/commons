package cash.atto.commons.signer

import java.net.ServerSocket

actual fun randomPort(): Int {
    ServerSocket(0).use { socket ->
        return socket.localPort
    }
}
