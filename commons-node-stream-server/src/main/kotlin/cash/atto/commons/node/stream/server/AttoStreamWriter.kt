package cash.atto.commons.node.stream.server

import cash.atto.commons.node.AttoStreamCommand
import cash.atto.commons.node.AttoStreamProtocol
import cash.atto.commons.node.AttoStreamResponse
import cash.atto.commons.node.AttoStreamResponseType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.JsonElement

/** Serializes data, membership barriers and cumulative acknowledgements for one connection. */
internal class AttoStreamWriter(
    private val errorResponse: (Throwable) -> AttoStreamResponse,
) {
    /** Only this writer assigns data sequence numbers and applies cumulative acknowledgements. */
    suspend fun write(
        controls: Channel<Control>,
        records: Channel<Record>,
        heartbeats: Channel<Unit>,
        outgoing: Channel<AttoStreamResponse>,
    ) {
        var started = false
        var active = emptySet<Any>()
        var lastSent = 0L
        var lastAcknowledged = 0L
        var pending: Record? = null
        var terminated = false
        while (currentCoroutineContext().isActive) {
            val record = pending
            if (record is Record.Value && record.token !in active) {
                pending = null
                continue
            }
            if (record is Record.Value && lastSent - lastAcknowledged < AttoStreamProtocol.CREDIT_WINDOW) {
                lastSent++
                outgoing.send(AttoStreamResponse(AttoStreamResponseType.DATA, data = record.value, sequence = lastSent.toString()))
                pending = null
                continue
            }
            if (record is Record.Terminal && lastSent == lastAcknowledged) {
                outgoing.send(record.response)
                outgoing.close()
                return
            }
            select<Unit> {
                controls.onReceive { control ->
                    when (control) {
                        Control.Begin -> {
                            started = true
                        }

                        is Control.Membership -> {
                            active = control.tokens
                            outgoing.send(AttoStreamResponse(control.responseType))
                        }

                        is Control.Response -> {
                            outgoing.send(control.response)
                        }

                        is Control.Fatal -> {
                            outgoing.send(control.response)
                            outgoing.close()
                            terminated = true
                        }

                        is Control.Ack -> {
                            val command = control.command
                            val sequence = command.sequence?.toLongOrNull()
                            if (!started ||
                                command.request != null ||
                                command.mutable ||
                                sequence == null ||
                                sequence < 0 ||
                                sequence > lastSent
                            ) {
                                outgoing.send(errorResponse(badStreamRequest("Invalid stream acknowledgement")))
                                outgoing.close()
                                terminated = true
                            } else if (sequence > lastAcknowledged) {
                                lastAcknowledged = sequence
                            }
                        }
                    }
                }
                heartbeats.onReceive { if (started) outgoing.send(AttoStreamResponse(AttoStreamResponseType.HEARTBEAT)) }
                if (pending == null) records.onReceive { pending = it }
            }
            if (terminated) return
        }
    }

    sealed interface Record {
        data class Value(
            val token: Any,
            val value: JsonElement,
        ) : Record

        data class Terminal(
            val response: AttoStreamResponse,
        ) : Record
    }

    sealed interface Control {
        data object Begin : Control

        data class Membership(
            val tokens: Set<Any>,
            val responseType: AttoStreamResponseType,
        ) : Control

        data class Ack(
            val command: AttoStreamCommand,
        ) : Control

        data class Response(
            val response: AttoStreamResponse,
        ) : Control

        data class Fatal(
            val response: AttoStreamResponse,
        ) : Control
    }
}
