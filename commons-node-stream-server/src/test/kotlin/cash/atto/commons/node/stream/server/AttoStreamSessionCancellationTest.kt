package cash.atto.commons.node.stream.server

import cash.atto.commons.node.AttoStreamCommand
import cash.atto.commons.node.AttoStreamCommandType
import cash.atto.commons.node.AttoStreamMethod
import cash.atto.commons.node.AttoStreamRequest
import cash.atto.commons.node.AttoStreamResponse
import cash.atto.commons.node.AttoStreamResponseType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AttoStreamSessionCancellationTest {
    @Test
    fun `producer self cancellation fails before or after data even when it returns normally`(): Unit =
        runTest {
            for (afterData in listOf(false, true)) {
                for (returnsNormally in listOf(false, true)) {
                    // Given
                    val cancel = CompletableDeferred<Unit>()
                    val stopped = CompletableDeferred<Unit>()
                    val expected = CancellationException("producer cancelled")
                    val source =
                        flow<JsonElement> {
                            try {
                                if (afterData) {
                                    emit(JsonPrimitive("accepted"))
                                    cancel.await()
                                }
                                currentCoroutineContext().cancel(expected)
                                if (!returnsNormally) awaitCancellation()
                            } finally {
                                stopped.complete(Unit)
                            }
                        }
                    val probe = Probe(this, AttoStreamSession({ _, _ -> plan(source) }))
                    try {
                        // When
                        probe.commands.send(Result.success(subscribe()))
                        runCurrent()
                        if (afterData) {
                            assertEquals(listOf(JsonPrimitive("accepted")), probe.data())
                            cancel.complete(Unit)
                            runCurrent()
                        }

                        // Then
                        probe.assertCancelled(expected.message)
                        assertTrue(stopped.isCompleted)
                        val responseCount = probe.responses.size
                        advanceTimeBy(2.seconds)
                        runCurrent()
                        assertEquals(responseCount, probe.responses.size)
                    } finally {
                        probe.stop()
                    }
                }
            }
        }

    @Test
    fun `member factory self cancellation cannot start or complete its returned flow`(): Unit =
        runTest {
            // Given
            var collected = false
            val expected = CancellationException("member factory cancelled")
            val session =
                AttoStreamSession({ _, _ ->
                    AttoStreamPlan(
                        mapOf(
                            "member" to
                                AttoStreamMember("fixed") {
                                    currentCoroutineContext().cancel(expected)
                                    flow { collected = true }
                                },
                        ),
                    )
                })
            val probe = Probe(this, session)
            try {
                // When
                probe.commands.send(Result.success(subscribe()))
                runCurrent()

                // Then
                probe.assertCancelled(expected.message)
                assertFalse(collected)
            } finally {
                probe.stop()
            }
        }

    @Test
    fun `planner self cancellation fails initial and updated subscriptions even when it returns normally`(): Unit =
        runTest {
            for (duringUpdate in listOf(false, true)) {
                for (returnsNormally in listOf(false, true)) {
                    // Given
                    val expected = CancellationException("planner cancelled")
                    val stopped = CompletableDeferred<Unit>()
                    val session =
                        AttoStreamSession({ request, _ ->
                            if (!duringUpdate || request.body != null) {
                                currentCoroutineContext().cancel(expected)
                                if (!returnsNormally) awaitCancellation()
                            }
                            plan(
                                flow {
                                    try {
                                        awaitCancellation()
                                    } finally {
                                        stopped.complete(Unit)
                                    }
                                },
                            )
                        })
                    val probe = Probe(this, session)
                    try {
                        // When
                        probe.commands.send(Result.success(subscribe(mutable = duringUpdate)))
                        runCurrent()
                        if (duringUpdate) {
                            probe.commands.send(Result.success(update("next")))
                            runCurrent()
                        }

                        // Then
                        probe.assertCancelled(expected.message)
                        if (duringUpdate) assertTrue(stopped.isCompleted)
                    } finally {
                        probe.stop()
                    }
                }
            }
        }

    @Test
    fun `input cancellation fails before or after subscribing including nested timeouts and normal returns`(): Unit =
        runTest {
            for (afterSubscribe in listOf(false, true)) {
                for (mode in listOf("timeout", "suspend", "return")) {
                    // Given
                    val cancel = CompletableDeferred<Unit>()
                    val expected = CancellationException("input cancelled")
                    val incoming =
                        flow {
                            if (afterSubscribe) {
                                emit(Result.success(subscribe()))
                                cancel.await()
                            }
                            if (mode == "timeout") {
                                withTimeout(100.milliseconds) { awaitCancellation() }
                            } else {
                                currentCoroutineContext().cancel(expected)
                                if (mode == "suspend") awaitCancellation()
                            }
                        }
                    val probe = Probe(this, AttoStreamSession({ _, _ -> plan(flow { awaitCancellation() }) }), incoming = incoming)
                    try {
                        // When
                        runCurrent()
                        cancel.complete(Unit)
                        advanceTimeBy(100.milliseconds)
                        runCurrent()

                        // Then
                        probe.assertCancelled(if (mode == "timeout") null else expected.message)
                    } finally {
                        probe.stop()
                    }
                }
            }
        }

    @Test
    fun `sender self cancellation cannot report successful completion`(): Unit =
        runTest {
            for (afterSubscribe in listOf(false, true)) {
                for (returnsNormally in listOf(false, true)) {
                    // Given
                    val commands = Channel<Result<AttoStreamCommand>>(Channel.UNLIMITED)
                    val outcome = CompletableDeferred<Result<Unit>>()
                    val expected = CancellationException("sender cancelled")
                    val session = AttoStreamSession({ _, _ -> plan(flow { awaitCancellation() }) })
                    val connection =
                        launch {
                            outcome.complete(
                                runCatching {
                                    session.run(commands.receiveAsFlow()) { outgoing ->
                                        if (afterSubscribe) outgoing.take(1).collect { }
                                        currentCoroutineContext().cancel(expected)
                                        if (!returnsNormally) awaitCancellation()
                                    }
                                },
                            )
                        }
                    try {
                        // When
                        commands.send(Result.success(subscribe()))
                        runCurrent()

                        // Then
                        assertTrue(outcome.isCompleted)
                        assertEquals(expected.message, assertIs<CancellationException>(outcome.await().exceptionOrNull()).message)
                    } finally {
                        connection.cancelAndJoin()
                        commands.cancel()
                    }
                }
            }
        }

    @Test
    fun `finite and empty streams complete normally while stopping their command reader`(): Unit =
        runTest {
            for (count in listOf(0, 2)) {
                // Given
                val probe = Probe(this, AttoStreamSession({ _, _ -> plan(flow { repeat(count) { emit(JsonPrimitive(it)) } }) }))
                try {
                    // When
                    probe.commands.send(Result.success(subscribe()))
                    runCurrent()

                    // Then
                    assertTrue(probe.outcome.isCompleted)
                    assertTrue(probe.outcome.await().isSuccess)
                    assertEquals(count, probe.data().size)
                    assertEquals(1, probe.responses.count { it.type == AttoStreamResponseType.COMPLETE })
                    assertTrue(probe.job.isCompleted)
                } finally {
                    probe.stop()
                }
            }
        }

    @Test
    fun `removal and rejected update preserve the retained producer until sealing`(): Unit =
        runTest {
            // Given
            val removed = CompletableDeferred<Unit>()
            val retained = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val session =
                AttoStreamSession({ request, _ ->
                    val body = request.body?.jsonPrimitive?.content ?: "ab"
                    if (body == "timeout") withTimeout(100.milliseconds) { awaitCancellation() }
                    AttoStreamPlan(
                        body.map { it.toString() }.associateWith { key ->
                            AttoStreamMember(key) {
                                flow {
                                    try {
                                        emit(JsonPrimitive(key))
                                        if (key == "a") awaitCancellation() else release.await()
                                    } finally {
                                        (if (key == "a") removed else retained).complete(Unit)
                                    }
                                }
                            }
                        },
                    )
                })
            val probe = Probe(this, session)
            try {
                probe.commands.send(Result.success(subscribe(mutable = true)))
                runCurrent()

                // When
                probe.commands.send(Result.success(update("timeout")))
                runCurrent()
                advanceTimeBy(100.milliseconds)
                runCurrent()
                val rejection = probe.responses.single { it.type == AttoStreamResponseType.ERROR }
                probe.commands.send(Result.success(update("b")))
                runCurrent()

                // Then
                assertEquals(504, rejection.status)
                assertFalse(rejection.terminal)
                assertTrue(removed.isCompleted)
                assertFalse(retained.isCompleted)
                assertFalse(probe.outcome.isCompleted)
                assertEquals(1, probe.responses.count { it.type == AttoStreamResponseType.UPDATED })
                probe.commands.send(Result.success(AttoStreamCommand(AttoStreamCommandType.SEAL)))
                release.complete(Unit)
                runCurrent()
                assertTrue(probe.outcome.await().isSuccess)
                assertTrue(retained.isCompleted)
                assertEquals(AttoStreamResponseType.COMPLETE, probe.responses.last().type)
            } finally {
                probe.stop()
            }
        }

    @Test
    fun `source failure cancels other producers without bypassing accepted data and final acknowledgement`(): Unit =
        runTest {
            // Given
            val fail = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            val session =
                AttoStreamSession({ _, _ ->
                    AttoStreamPlan(
                        mapOf(
                            "failed" to
                                AttoStreamMember("fixed") {
                                    flow {
                                        fail.await()
                                        repeat(45) { emit(JsonPrimitive(it)) }
                                        error("backend failed")
                                    }
                                },
                            "other" to
                                AttoStreamMember("fixed") {
                                    flow {
                                        try {
                                            awaitCancellation()
                                        } finally {
                                            stopped.complete(Unit)
                                        }
                                    }
                                },
                        ),
                    )
                })
            val probe = Probe(this, session, autoAcknowledge = false)
            try {
                probe.commands.send(Result.success(subscribe()))
                runCurrent()

                // When
                fail.complete(Unit)
                runCurrent()

                // Then
                assertTrue(stopped.isCompleted)
                assertEquals(32, probe.data().size)
                assertFalse(probe.outcome.isCompleted)
                probe.commands.send(Result.success(acknowledge("32")))
                runCurrent()
                assertEquals((0 until 45).map { JsonPrimitive(it) }, probe.data())
                assertFalse(probe.outcome.isCompleted)
                probe.commands.send(Result.success(acknowledge("45")))
                runCurrent()
                assertTrue(probe.outcome.await().isSuccess)
                assertEquals(500, probe.responses.last().status)
                assertTrue(probe.responses.last().terminal)
                assertFalse(probe.responses.any { it.type == AttoStreamResponseType.COMPLETE })
            } finally {
                probe.stop()
            }
        }

    private class Probe(
        scope: CoroutineScope,
        session: AttoStreamSession,
        autoAcknowledge: Boolean = true,
        val commands: Channel<Result<AttoStreamCommand>> = Channel(Channel.UNLIMITED),
        incoming: Flow<Result<AttoStreamCommand>> = commands.receiveAsFlow(),
    ) {
        val responses = mutableListOf<AttoStreamResponse>()
        val outcome = CompletableDeferred<Result<Unit>>()
        val job =
            scope.launch {
                outcome.complete(
                    runCatching {
                        session.run(incoming) { outgoing ->
                            outgoing.collect { response ->
                                responses.add(response)
                                if (autoAcknowledge && response.type == AttoStreamResponseType.DATA) {
                                    commands.send(Result.success(acknowledge(requireNotNull(response.sequence))))
                                }
                            }
                        }
                    },
                )
            }

        fun data(): List<JsonElement?> = responses.filter { it.type == AttoStreamResponseType.DATA }.map { it.data }

        fun assertCancelled(message: String?) {
            assertTrue(outcome.isCompleted, "Cancelled child left the session active")
            val failure = assertIs<CancellationException>(outcome.getCompleted().exceptionOrNull())
            if (message != null) assertEquals(message, failure.message)
            assertFalse(responses.any { it.type == AttoStreamResponseType.COMPLETE })
            assertTrue(job.isCompleted)
        }

        suspend fun stop() {
            job.cancelAndJoin()
            commands.cancel()
        }
    }

    private companion object {
        fun subscribe(mutable: Boolean = false): AttoStreamCommand =
            AttoStreamCommand(AttoStreamCommandType.SUBSCRIBE, AttoStreamRequest(AttoStreamMethod.GET, "/v2/accounts/stream"), mutable)

        fun update(body: String): AttoStreamCommand =
            AttoStreamCommand(
                AttoStreamCommandType.UPDATE,
                AttoStreamRequest(AttoStreamMethod.GET, "/v2/accounts/stream", JsonPrimitive(body)),
            )

        fun acknowledge(sequence: String): AttoStreamCommand = AttoStreamCommand(AttoStreamCommandType.ACK, sequence = sequence)

        fun plan(source: Flow<JsonElement>): AttoStreamPlan = AttoStreamPlan(mapOf("member" to AttoStreamMember("fixed") { source }))
    }
}
