package cash.atto.commons.node.stream.server

import cash.atto.commons.node.AttoStreamCommand
import cash.atto.commons.node.AttoStreamCommandType
import cash.atto.commons.node.AttoStreamRequest
import cash.atto.commons.node.AttoStreamResponse
import cash.atto.commons.node.AttoStreamResponseType
import cash.atto.commons.node.stream.server.AttoStreamWriter.Record
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import cash.atto.commons.node.stream.server.AttoStreamWriter.Control as WriteControl

/** Owns one subscription's membership, child producers and connection lifetime. */
class AttoStreamSession(
    private val planner: suspend (AttoStreamRequest, Boolean) -> AttoStreamPlan,
    private val heartbeatInterval: Duration = 1.seconds,
    private val mapError: (Throwable) -> AttoStreamError? = { null },
) {
    init {
        require(heartbeatInterval.isPositive() && heartbeatInterval.isFinite()) { "Heartbeat interval must be positive and finite" }
    }

    suspend fun run(
        incoming: Flow<Result<AttoStreamCommand>>,
        send: suspend (Flow<AttoStreamResponse>) -> Unit,
    ): Unit =
        coroutineScope {
            val finished = CompletableDeferred<Unit>()
            val commands = Channel<Control>(QUEUE_CAPACITY)
            val controls = Channel<WriteControl>(QUEUE_CAPACITY)
            val records = Channel<Record>(QUEUE_CAPACITY)
            val heartbeats = Channel<Unit>(Channel.CONFLATED)
            val outgoing = Channel<AttoStreamResponse>(Channel.RENDEZVOUS)
            launch {
                try {
                    send(outgoing.receiveAsFlow())
                    finished.complete(Unit)
                } catch (failure: Throwable) {
                    finished.completeExceptionally(failure)
                }
            }
            launch {
                while (isActive) {
                    delay(heartbeatInterval)
                    heartbeats.trySend(Unit)
                }
            }
            val setupTimeout =
                launch {
                    delay(REQUEST_TIMEOUT_MILLIS)
                    commands.send(Control.Failed(badStreamRequest("Stream subscription was not received in time")))
                }
            launch { AttoStreamWriter { errorResponse(it) }.write(controls, records, heartbeats, outgoing) }
            launch {
                try {
                    incoming.collect { parsed ->
                        val command =
                            parsed.getOrElse { failure ->
                                commands.send(Control.Failed(failure))
                                // Keep the incoming transport alive until the protocol error has flushed.
                                awaitCancellation()
                            }
                        // Credits drain even while the planner or a terminal FIFO marker is suspended.
                        if (command.type == AttoStreamCommandType.ACK) {
                            controls.send(WriteControl.Ack(command))
                        } else {
                            commands.send(Control.Command(command))
                        }
                    }
                    finished.complete(Unit)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Throwable) {
                    commands.send(Control.Failed(failure))
                }
            }
            launch {
                val members = linkedMapOf<String, ActiveMember>()
                var subscription: AttoStreamRequest? = null
                var mutable = false
                var sealed = false
                var terminalQueued = false

                suspend fun cancelMembers() {
                    members.values.forEach { it.job.cancel() }
                    members.values.forEach { it.job.join() }
                }

                suspend fun completeIfFinished() {
                    if (!terminalQueued && subscription != null && (!mutable || sealed) && members.values.all { it.completed }) {
                        terminalQueued = true
                        // Every producer has finished, so this marker stays after all accepted data without blocking controls.
                        launch { records.send(Record.Terminal(AttoStreamResponse(AttoStreamResponseType.COMPLETE))) }
                    }
                }

                suspend fun applyPlan(
                    plan: AttoStreamPlan,
                    responseType: AttoStreamResponseType,
                ) {
                    // Validate all retained definitions before changing any child.
                    plan.members.forEach { (key, definition) ->
                        val retained = members[key]
                        if (retained != null && retained.definition.signature != definition.signature) {
                            throw badStreamRequest("Filters and ranges of retained stream members cannot change")
                        }
                    }
                    members.keys.filter { it !in plan.members }.forEach { key -> members.remove(key)!!.job.cancelAndJoin() }
                    val added = mutableListOf<ActiveMember>()
                    plan.members.forEach { (key, definition) ->
                        if (key !in members) {
                            val token = Any()
                            val job =
                                launch(start = CoroutineStart.LAZY) {
                                    try {
                                        definition.stream().collect { records.send(Record.Value(token, it)) }
                                        commands.send(Control.Completed(key, token))
                                    } catch (failure: Throwable) {
                                        if (failure is CancellationException && !currentCoroutineContext().isActive) throw failure
                                        commands.send(Control.MemberFailed(token, failure))
                                    }
                                }
                            ActiveMember(definition, token, job).also {
                                members[key] = it
                                added.add(it)
                            }
                        }
                    }
                    controls.send(WriteControl.Membership(members.values.mapTo(HashSet()) { it.token }, responseType))
                    added.forEach { it.job.start() }
                }
                try {
                    for (control in commands) {
                        when (control) {
                            is Control.Command -> {
                                val command = control.value
                                if (subscription == null) {
                                    setupTimeout.cancel()
                                    if (command.type != AttoStreamCommandType.SUBSCRIBE ||
                                        command.request == null ||
                                        command.sequence != null
                                    ) {
                                        throw badStreamRequest("The first command must subscribe to a stream")
                                    }
                                    controls.send(WriteControl.Begin)
                                    val initialRequest = requireNotNull(command.request)
                                    val plan = planner(initialRequest, command.mutable)
                                    subscription = initialRequest
                                    mutable = command.mutable
                                    applyPlan(plan, AttoStreamResponseType.SUBSCRIBED)
                                    completeIfFinished()
                                } else {
                                    when (command.type) {
                                        AttoStreamCommandType.UPDATE -> {
                                            try {
                                                if (!mutable ||
                                                    sealed ||
                                                    terminalQueued
                                                ) {
                                                    throw badStreamRequest("This stream cannot be updated")
                                                }
                                                val next = command.request ?: throw badStreamRequest("Missing updated stream request")
                                                if (command.sequence != null ||
                                                    command.mutable ||
                                                    next.method != subscription.method ||
                                                    next.path != subscription.path
                                                ) {
                                                    throw badStreamRequest("An update may change only the subscription body")
                                                }
                                                applyPlan(planner(next, true), AttoStreamResponseType.UPDATED)
                                            } catch (failure: Throwable) {
                                                if (failure is CancellationException &&
                                                    !currentCoroutineContext().isActive
                                                ) {
                                                    throw failure
                                                }
                                                controls.send(WriteControl.Response(errorResponse(failure, terminal = false)))
                                            }
                                        }

                                        AttoStreamCommandType.SEAL -> {
                                            if (!mutable ||
                                                sealed ||
                                                command.request != null ||
                                                command.sequence != null ||
                                                command.mutable
                                            ) {
                                                controls.send(
                                                    WriteControl.Response(
                                                        errorResponse(badStreamRequest("Invalid stream seal"), terminal = false),
                                                    ),
                                                )
                                            } else {
                                                sealed = true
                                                completeIfFinished()
                                            }
                                        }

                                        else -> {
                                            throw badStreamRequest("Only one subscription is allowed per connection")
                                        }
                                    }
                                }
                            }

                            is Control.Completed -> {
                                members[control.key]?.takeIf { it.token === control.token }?.completed = true
                                completeIfFinished()
                            }

                            is Control.MemberFailed -> {
                                if (members.values.any { it.token === control.token } && !terminalQueued) {
                                    terminalQueued = true
                                    cancelMembers()
                                    // Accepted records precede the failure marker, including records from other members.
                                    launch { records.send(Record.Terminal(errorResponse(control.failure))) }
                                }
                            }

                            is Control.Failed -> {
                                throw control.failure
                            }
                        }
                    }
                } catch (failure: Throwable) {
                    if (failure is CancellationException && !currentCoroutineContext().isActive) throw failure
                    cancelMembers()
                    controls.send(WriteControl.Fatal(errorResponse(failure)))
                }
            }
            try {
                finished.await()
            } finally {
                currentCoroutineContext().cancelChildren()
                outgoing.cancel()
                records.cancel()
                controls.cancel()
                commands.cancel()
                heartbeats.cancel()
            }
        }

    private fun errorResponse(
        failure: Throwable,
        terminal: Boolean = true,
    ): AttoStreamResponse {
        val error =
            when (failure) {
                is AttoStreamRequestException -> AttoStreamError(400, failure.reason)
                is TimeoutCancellationException -> AttoStreamError(504, "Stream operation timed out")
                else -> mapError(failure) ?: AttoStreamError(500, "Stream operation failed")
            }
        return AttoStreamResponse(AttoStreamResponseType.ERROR, status = error.status, message = error.message, terminal = terminal)
    }

    private class ActiveMember(
        val definition: AttoStreamMember,
        val token: Any,
        val job: Job,
        var completed: Boolean = false,
    )

    private sealed interface Control {
        data class Command(
            val value: AttoStreamCommand,
        ) : Control

        data class Completed(
            val key: String,
            val token: Any,
        ) : Control

        data class MemberFailed(
            val token: Any,
            val failure: Throwable,
        ) : Control

        data class Failed(
            val failure: Throwable,
        ) : Control
    }

    private companion object {
        const val QUEUE_CAPACITY = 64
        const val REQUEST_TIMEOUT_MILLIS = 10_000L
    }
}
