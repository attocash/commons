package cash.atto.commons.node

import cash.atto.commons.transport.AttoWebSocketTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

internal class AttoNodeWebSocketStreams(
    private val transport: AttoWebSocketTransport,
    private val json: Json,
) {
    fun <T> stream(
        serializer: KSerializer<T>,
        requests: Flow<AttoStreamRequest>,
        mutable: Boolean,
        requestTimeout: Duration = Duration.INFINITE,
        idleTimeout: Duration = 1.hours,
        connectTimeout: Duration = 10.seconds,
    ): Flow<T> =
        flow {
            withTimeoutOrNull(requestTimeout) {
                coroutineScope {
                    val commands = Channel<String>(AttoStreamProtocol.CREDIT_WINDOW)
                    val records = Channel<Pair<String, JsonElement>>(AttoStreamProtocol.CREDIT_WINDOW)

                    val updates =
                        launch {
                            try {
                                var first = true
                                requests.collect { request ->
                                    val type = if (first) AttoStreamCommandType.SUBSCRIBE else AttoStreamCommandType.UPDATE
                                    commands.send(AttoStreamProtocol.encodeCommand(AttoStreamCommand(type, request, mutable && first)))
                                    first = false
                                }
                                coroutineContext.ensureActive()
                                if (first) {
                                    commands.close()
                                } else if (mutable) {
                                    commands.send(AttoStreamProtocol.encodeCommand(AttoStreamCommand(AttoStreamCommandType.SEAL)))
                                }
                            } catch (failure: CancellationException) {
                                if (failure !is StreamCleanupCancellation) this@coroutineScope.cancel(failure)
                                throw failure
                            }
                        }
                    val initial = commands.receiveCatching().getOrNull()
                    if (initial == null) {
                        updates.join()
                        return@coroutineScope
                    }
                    val outgoing =
                        flow {
                            emit(initial)
                            for (command in commands) emit(command)
                        }
                    val reader =
                        launch {
                            var lastSequence = 0UL
                            try {
                                transport
                                    .exchange(AttoStreamProtocol.PATH, outgoing, connectTimeout, idleTimeout)
                                    .takeWhile { text ->
                                        val response = AttoStreamProtocol.decodeResponse(text)
                                        when (response.type) {
                                            AttoStreamResponseType.DATA -> {
                                                val data = requireNotNull(response.data) { "Missing WebSocket stream data" }
                                                require(
                                                    data.toString().length <= AttoStreamProtocol.MAX_RECORD_CHARS,
                                                ) { "WebSocket stream record is too large" }
                                                val sequence = requireNotNull(response.sequence) { "Missing WebSocket data sequence" }
                                                require(
                                                    sequence.toULongOrNull() == lastSequence + 1UL,
                                                ) { "Invalid WebSocket data sequence" }
                                                lastSequence++
                                                records.send(sequence to data)
                                                true
                                            }

                                            AttoStreamResponseType.COMPLETE -> {
                                                false
                                            }

                                            AttoStreamResponseType.ERROR -> {
                                                throw AttoNodeStreamException(
                                                    requireNotNull(response.status) { "Missing WebSocket error status" },
                                                    requireNotNull(response.message) { "Missing WebSocket error message" },
                                                )
                                            }

                                            else -> {
                                                true
                                            }
                                        }
                                    }.collect { }
                                coroutineContext.ensureActive()
                            } catch (failure: CancellationException) {
                                if (failure !is StreamCleanupCancellation) this@coroutineScope.cancel(failure)
                                throw failure
                            } catch (failure: Throwable) {
                                // Deliver preceding valid records before surfacing the terminal stream failure.
                                records.close(failure)
                            } finally {
                                records.close()
                            }
                        }
                    try {
                        for ((sequence, data) in records) {
                            emit(json.decodeFromJsonElement(serializer, data))
                            val acknowledgement =
                                AttoStreamProtocol.encodeCommand(
                                    AttoStreamCommand(AttoStreamCommandType.ACK, sequence = sequence),
                                )
                            select<Unit> {
                                commands.onSend(acknowledgement) { }
                                // A closed reader has no sender; a queued membership update must not block error delivery.
                                reader.onJoin { }
                            }
                        }
                        reader.join()
                    } finally {
                        val cleanup = StreamCleanupCancellation()
                        reader.cancel(cleanup)
                        updates.cancel(cleanup)
                        commands.cancel()
                        records.cancel()
                    }
                }
                true
            } ?: error("WebSocket stream request timeout")
        }
}

private class StreamCleanupCancellation : CancellationException("WebSocket stream collection ended")
