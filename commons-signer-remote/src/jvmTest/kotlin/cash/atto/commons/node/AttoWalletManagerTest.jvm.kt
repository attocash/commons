package cash.atto.commons.node

import java.net.ServerSocket

actual fun randomPort(): Int {
    ServerSocket(0).use { socket ->
        return socket.localPort
    }
}
