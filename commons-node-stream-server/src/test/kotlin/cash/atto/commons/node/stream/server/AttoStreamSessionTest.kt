package cash.atto.commons.node.stream.server

import cash.atto.commons.node.AttoStreamCommand
import cash.atto.commons.node.AttoStreamCommandType
import cash.atto.commons.node.AttoStreamMethod
import cash.atto.commons.node.AttoStreamProtocol
import cash.atto.commons.node.AttoStreamRequest
import cash.atto.commons.node.AttoStreamResponse
import cash.atto.commons.node.AttoStreamResponseType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AttoStreamSessionTest {
    @Test
    fun `cumulative credits release records while completion waits for the final acknowledgement`() =
        runTest {
            // Given
            val recordCount = AttoStreamProtocol.CREDIT_WINDOW + 2
            val session =
                AttoStreamSession({ _, _ ->
                    plan(
                        flow {
                            repeat(recordCount) { emit(JsonPrimitive(it)) }
                        },
                    )
                })
            val commands = Channel<Result<AttoStreamCommand>>(Channel.UNLIMITED)
            val responses = mutableListOf<AttoStreamResponse>()
            val connection = launch { session.run(commands.receiveAsFlow()) { outgoing -> outgoing.collect { responses.add(it) } } }
            try {
                // When
                commands.send(Result.success(subscribe()))
                runCurrent()
                val initialSequences = responses.filter { it.type == AttoStreamResponseType.DATA }.map { it.sequence }
                commands.send(Result.success(acknowledge(2)))
                runCurrent()
                val creditedSequences = responses.filter { it.type == AttoStreamResponseType.DATA }.map { it.sequence }
                val completeBeforeFinalAcknowledgement = responses.any { it.type == AttoStreamResponseType.COMPLETE }
                commands.send(Result.success(acknowledge(recordCount)))
                connection.join()

                // Then
                assertEquals(AttoStreamResponseType.SUBSCRIBED, responses.first().type)
                assertEquals((1..AttoStreamProtocol.CREDIT_WINDOW).map { it.toString() }, initialSequences)
                assertEquals((1..recordCount).map { it.toString() }, creditedSequences)
                assertFalse(completeBeforeFinalAcknowledgement)
                assertEquals(AttoStreamResponseType.COMPLETE, responses.last().type)
                assertEquals(1, responses.count { it.type == AttoStreamResponseType.COMPLETE })
            } finally {
                commands.cancel()
                connection.cancelAndJoin()
            }
        }

    @Test
    fun `incoming disconnect cancels the active producer and finishes the session`() =
        runTest {
            // Given
            val producerStarted = CompletableDeferred<Unit>()
            val producerStopped = CompletableDeferred<Unit>()
            val session =
                AttoStreamSession({ _, _ ->
                    plan(
                        flow {
                            producerStarted.complete(Unit)
                            try {
                                awaitCancellation()
                            } finally {
                                producerStopped.complete(Unit)
                            }
                        },
                    )
                })
            val commands = Channel<Result<AttoStreamCommand>>(Channel.UNLIMITED)
            val responses = mutableListOf<AttoStreamResponse>()
            val connection = launch { session.run(commands.receiveAsFlow()) { outgoing -> outgoing.collect { responses.add(it) } } }
            try {
                commands.send(Result.success(subscribe()))
                producerStarted.await()
                runCurrent()

                // When
                commands.close()
                connection.join()

                // Then
                assertTrue(producerStopped.isCompleted)
                assertTrue(connection.isCompleted)
                assertEquals(listOf(AttoStreamResponseType.SUBSCRIBED), responses.map { it.type })
            } finally {
                commands.cancel()
                connection.cancelAndJoin()
            }
        }

    @Test
    fun `unclassified producer failure returns a sanitized internal error`() =
        runTest {
            // Given
            val session =
                AttoStreamSession({ _, _ ->
                    plan(flow { throw IllegalStateException("private backend details") })
                })

            // When
            val responses = completeSession(session, Result.success(subscribe()))

            // Then
            assertTerminalError(responses, 500, "Stream operation failed")
            assertEquals(AttoStreamResponseType.SUBSCRIBED, responses.first().type)
        }

    @Test
    fun `producer timeout becomes a gateway timeout while heartbeats continue`() =
        runTest {
            // Given
            val session =
                AttoStreamSession({ _, _ ->
                    plan(flow { withTimeout(3.seconds) { awaitCancellation() } })
                })
            val commands = Channel<Result<AttoStreamCommand>>(Channel.UNLIMITED)
            val responses = mutableListOf<AttoStreamResponse>()
            val connection = launch { session.run(commands.receiveAsFlow()) { outgoing -> outgoing.collect { responses.add(it) } } }
            try {
                commands.send(Result.success(subscribe()))
                runCurrent()

                // When
                advanceTimeBy(2.seconds)
                runCurrent()
                val heartbeatBeforeTimeout = responses.any { it.type == AttoStreamResponseType.HEARTBEAT }
                val failedBeforeTimeout = responses.any { it.type == AttoStreamResponseType.ERROR }
                advanceTimeBy(1.seconds)
                runCurrent()
                connection.join()

                // Then
                assertTrue(heartbeatBeforeTimeout)
                assertFalse(failedBeforeTimeout)
                assertTerminalError(responses, 504, "Stream operation timed out")
            } finally {
                commands.cancel()
                connection.cancelAndJoin()
            }
        }

    @Test
    fun `adapter classifies a failed incoming command without invoking the planner`() =
        runTest {
            // Given
            val invalidPayload = IllegalArgumentException("invalid adapter payload")
            var planningCalls = 0
            val session =
                AttoStreamSession(
                    planner = { _, _ ->
                        planningCalls++
                        AttoStreamPlan(emptyMap())
                    },
                    mapError = { failure ->
                        if (failure === invalidPayload) AttoStreamError(422, "Unsupported command payload") else null
                    },
                )

            // When
            val responses = completeSession(session, Result.failure(invalidPayload))

            // Then
            assertEquals(0, planningCalls)
            assertTerminalError(responses, 422, "Unsupported command payload")
            assertEquals(1, responses.size)
        }

    @Test
    fun `positive submillisecond heartbeat allows subscription and disconnect to progress`(): Unit =
        Executors
            .newSingleThreadExecutor { task ->
                Thread(task, "atto-stream-heartbeat-regression").apply { isDaemon = true }
            }.asCoroutineDispatcher()
            .use { dispatcher ->
                runBlocking {
                    // Given
                    val producerStarted = CompletableDeferred<Unit>()
                    val producerStopped = CompletableDeferred<Unit>()
                    val session =
                        AttoStreamSession(
                            planner = { _, _ ->
                                plan(
                                    flow {
                                        producerStarted.complete(Unit)
                                        try {
                                            awaitCancellation()
                                        } finally {
                                            producerStopped.complete(Unit)
                                        }
                                    },
                                )
                            },
                            heartbeatInterval = 500.microseconds,
                        )
                    val commands = Channel<Result<AttoStreamCommand>>(Channel.UNLIMITED)
                    val responses = Channel<AttoStreamResponse>(Channel.UNLIMITED)
                    val connection =
                        launch(dispatcher) { session.run(commands.receiveAsFlow()) { outgoing -> outgoing.collect { responses.send(it) } } }
                    try {
                        // When
                        // Keep the timeout outside the session dispatcher so cancellation also stops a non-yielding heartbeat.
                        val observed =
                            withTimeout(2.seconds) {
                                commands.send(Result.success(subscribe()))
                                val types = mutableSetOf<AttoStreamResponseType>()
                                while (AttoStreamResponseType.SUBSCRIBED !in types || AttoStreamResponseType.HEARTBEAT !in types) {
                                    types += responses.receive().type
                                }
                                producerStarted.await()
                                commands.close()
                                connection.join()
                                types
                            }

                        // Then
                        assertTrue(AttoStreamResponseType.SUBSCRIBED in observed)
                        assertTrue(AttoStreamResponseType.HEARTBEAT in observed)
                        assertTrue(producerStopped.isCompleted)
                    } finally {
                        connection.cancelAndJoin()
                        commands.cancel()
                        responses.cancel()
                    }
                }
            }

    private fun subscribe(): AttoStreamCommand =
        AttoStreamCommand(
            AttoStreamCommandType.SUBSCRIBE,
            request = AttoStreamRequest(AttoStreamMethod.GET, "/accounts"),
        )

    private fun acknowledge(sequence: Int): AttoStreamCommand = AttoStreamCommand(AttoStreamCommandType.ACK, sequence = sequence.toString())

    private fun plan(stream: Flow<JsonElement>): AttoStreamPlan =
        AttoStreamPlan(mapOf("member" to AttoStreamMember("fixed filters") { stream }))

    private suspend fun completeSession(
        session: AttoStreamSession,
        command: Result<AttoStreamCommand>,
    ): List<AttoStreamResponse> {
        val commands = Channel<Result<AttoStreamCommand>>(Channel.UNLIMITED)
        val responses = mutableListOf<AttoStreamResponse>()
        try {
            commands.send(command)
            session.run(commands.receiveAsFlow()) { outgoing -> outgoing.collect { responses.add(it) } }
        } finally {
            commands.cancel()
        }
        return responses
    }

    private fun assertTerminalError(
        responses: List<AttoStreamResponse>,
        status: Int,
        message: String,
    ) {
        val error = responses.single { it.type == AttoStreamResponseType.ERROR }
        assertEquals(status, error.status)
        assertEquals(message, error.message)
        assertTrue(error.terminal)
        assertEquals(error, responses.last())
        assertFalse(responses.any { it.type == AttoStreamResponseType.COMPLETE })
    }
}
