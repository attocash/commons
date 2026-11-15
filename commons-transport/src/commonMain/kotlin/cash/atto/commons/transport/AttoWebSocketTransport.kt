package cash.atto.commons.transport

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.headers
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.util.PlatformUtils
import io.ktor.websocket.ChannelOverflow
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val MAX_WEBSOCKET_MESSAGE_BYTES: Long = 256 * 1024

/** The collector owns the connection, its incoming reader, and the single serialized command sender. */
class AttoWebSocketTransport(
    private val baseUrl: String,
    private val headerProvider: suspend () -> Map<String, String> = { emptyMap() },
) {
    internal var client: HttpClient = sharedWebSocketClient

    fun exchange(
        path: String,
        commands: Flow<String>,
        connectTimeout: Duration,
        idleTimeout: Duration,
    ): Flow<String> =
        channelFlow {
            val requestHeaders = headerProvider()
            require(!PlatformUtils.IS_BROWSER || requestHeaders.isEmpty()) {
                "Browser WebSocket connections do not support custom headers; use HTTP streaming instead"
            }
            try {
                coroutineScope {
                    val opened = CompletableDeferred<Unit>()
                    val connection =
                        async {
                            client.webSocket(url(path), request = {
                                headers { requestHeaders.forEach { (name, value) -> append(name, value) } }
                            }) {
                                opened.complete(Unit)
                                val sender = launch { commands.collect { send(Frame.Text(it)) } }
                                try {
                                    while (true) {
                                        val result =
                                            withTimeoutOrNull(idleTimeout) { incoming.receiveCatching() }
                                                ?: error("WebSocket receive-idle timeout")
                                        val frame =
                                            result.getOrNull()
                                                ?: throw (
                                                    result.exceptionOrNull()
                                                        ?: IllegalStateException("WebSocket closed before stream completion")
                                                )
                                        when (frame) {
                                            is Frame.Text -> {
                                                require(frame.data.size <= MAX_WEBSOCKET_MESSAGE_BYTES) { "WebSocket message is too large" }
                                                this@channelFlow.send(frame.readText())
                                            }

                                            is Frame.Close -> {
                                                error("WebSocket closed before stream completion")
                                            }

                                            else -> {
                                                error("Expected a JSON text WebSocket message")
                                            }
                                        }
                                    }
                                } finally {
                                    sender.cancel()
                                    try {
                                        withContext(NonCancellable) { withTimeoutOrNull(250.milliseconds) { close() } }
                                    } finally {
                                        coroutineContext[kotlinx.coroutines.Job]?.cancel()
                                    }
                                }
                            }
                        }
                    try {
                        withTimeoutOrNull(connectTimeout) {
                            opened.await()
                            true
                        } ?: error("WebSocket connection timeout")
                        connection.await()
                    } finally {
                        connection.cancel()
                    }
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                // Preserve buffered terminal protocol frames before reporting a subsequent transport close.
                close(failure)
            }
        }

    private fun url(path: String): String =
        URLBuilder("${baseUrl.trimEnd('/')}/${path.trimStart('/')}")
            .apply {
                protocol =
                    when (protocol) {
                        URLProtocol.HTTP -> URLProtocol.WS
                        URLProtocol.HTTPS -> URLProtocol.WSS
                        else -> error("WebSocket transport requires an HTTP or HTTPS base URL")
                    }
            }.buildString()
}

private val sharedWebSocketClient =
    HttpClient {
        install(WebSockets) {
            // Browser/Node engines expose no native frame-limit setter; validate their received bytes before decoding.
            if (PlatformUtils.IS_JVM) maxFrameSize = MAX_WEBSOCKET_MESSAGE_BYTES
            channels {
                // Browser callbacks cannot suspend: overflow must fail instead of silently dropping records.
                incoming = bounded(64, ChannelOverflow.CLOSE)
                outgoing = bounded(1)
            }
        }
        expectSuccess = true
    }
