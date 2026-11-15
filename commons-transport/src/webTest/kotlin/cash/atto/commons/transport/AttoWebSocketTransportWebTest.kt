@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.transport

import io.ktor.util.PlatformUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class AttoWebSocketTransportWebTest {
    @Test
    fun `Node JS and Wasm exchange text and preserve supported headers`() =
        runTest {
            if (PlatformUtils.IS_BROWSER) return@runTest
            // Given
            val server = startServer().await()
            try {
                val transport = AttoWebSocketTransport("http://127.0.0.1:${port(server)}/api") { mapOf("X-Test" to "present") }
                // When
                val actual =
                    withContext(Dispatchers.Default) {
                        transport.exchange("/v2/stream", flowOf("hello"), 5.seconds, 5.seconds).take(2).toList()
                    }
                // Then
                assertEquals(listOf("hello", "present"), actual)
            } finally {
                stopServer(server).await()
            }
        }

    @Test
    fun `Node JS and Wasm burst overflow fails visibly for a slow consumer`() =
        runTest {
            if (PlatformUtils.IS_BROWSER) return@runTest
            // Given
            val server = startServer().await()
            try {
                val transport = AttoWebSocketTransport("http://127.0.0.1:${port(server)}")
                // When
                val failure =
                    withContext(Dispatchers.Default) {
                        runCatching {
                            transport
                                .exchange("/burst", flowOf("burst"), 5.seconds, 5.seconds)
                                .onEach { delay(10) }
                                .toList()
                        }.exceptionOrNull()
                    }
                // Then
                assertNotNull(failure)
            } finally {
                stopServer(server).await()
            }
        }
}

private fun startServer(): Promise<JsAny> =
    js(
        """import('ws').then(({ WebSocketServer }) => new Promise(resolve => {
    const server = new WebSocketServer({ port: 0, host: '127.0.0.1' });
    server.on('connection', (socket, request) => socket.on('message', data => {
        if (request.url === '/burst') {
            for (let i = 0; i < 10000; i++) socket.send('record-' + i);
        } else {
            socket.send(data.toString());
            socket.send(request.headers['x-test'] || 'missing');
        }
    }));
    server.on('listening', () => resolve(server));
}))""",
    )

private fun port(server: JsAny): Int = js("server.address().port")

private fun stopServer(server: JsAny): Promise<JsAny> =
    js(
        """new Promise(resolve => {
    for (const socket of server.clients) socket.terminate();
    server.close(() => resolve(null));
})""",
    )
