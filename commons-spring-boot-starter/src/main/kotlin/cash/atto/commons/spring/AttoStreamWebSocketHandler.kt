package cash.atto.commons.spring

import cash.atto.commons.node.AttoStreamProtocol
import cash.atto.commons.node.AttoStreamRequest
import cash.atto.commons.node.stream.server.AttoStreamError
import cash.atto.commons.node.stream.server.AttoStreamPlan
import cash.atto.commons.node.stream.server.AttoStreamSession
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import kotlinx.serialization.SerializationException
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.socket.HandshakeInfo
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.time.Duration
import kotlin.time.toKotlinDuration

/** Adapts Spring WebSocket payloads and connection completion to one logical stream session. */
class AttoStreamWebSocketHandler(
    private val planner: suspend (AttoStreamRequest, HandshakeInfo, Boolean) -> AttoStreamPlan,
    private val heartbeatInterval: Duration = Duration.ofSeconds(1),
) : WebSocketHandler {
    init {
        require(!heartbeatInterval.isZero && !heartbeatInterval.isNegative) { "Heartbeat interval must be positive" }
    }

    override fun handle(session: WebSocketSession): Mono<Void> =
        mono {
            val incoming =
                flow {
                    emitAll(
                        session
                            .receive()
                            .filter { it.type != WebSocketMessage.Type.PING && it.type != WebSocketMessage.Type.PONG }
                            // Copy pooled payload before the coroutine bridge retains it.
                            .map { message ->
                                runCatching {
                                    if (message.type != WebSocketMessage.Type.TEXT) {
                                        throw badRequest("Stream commands must be text messages")
                                    }
                                    val text = message.payloadAsText
                                    if (text.length > MAX_FRAME_PAYLOAD_LENGTH) throw badRequest("Stream command is too large")
                                    try {
                                        AttoStreamProtocol.decodeCommand(text)
                                    } catch (failure: SerializationException) {
                                        throw badRequest("Invalid stream command", failure)
                                    } catch (failure: IllegalArgumentException) {
                                        throw badRequest("Invalid stream command", failure)
                                    }
                                }
                            }.asFlow()
                            .buffer(0),
                    )
                }
            AttoStreamSession(
                planner = { request, mutable -> planner(request, session.handshakeInfo, mutable) },
                heartbeatInterval = heartbeatInterval.toKotlinDuration(),
                mapError = { failure ->
                    if (failure is ResponseStatusException) {
                        AttoStreamError(failure.statusCode.value(), failure.reason ?: "Stream request failed")
                    } else {
                        null
                    }
                },
            ).run(incoming) { responses ->
                session
                    .send(
                        responses
                            .map { session.textMessage(AttoStreamProtocol.encodeResponse(it)) }
                            .buffer(0)
                            .asFlux(),
                    ).awaitSingleOrNull()
            }
        }.then()

    private fun badRequest(
        message: String,
        cause: Throwable? = null,
    ): ResponseStatusException = ResponseStatusException(HttpStatus.BAD_REQUEST, message, cause)

    companion object {
        const val MAX_FRAME_PAYLOAD_LENGTH: Int = 256 * 1024
    }
}
