package cash.atto.commons.spring

import cash.atto.commons.node.AttoStreamCommand
import cash.atto.commons.node.AttoStreamCommandType
import cash.atto.commons.node.AttoStreamMethod
import cash.atto.commons.node.AttoStreamProtocol
import cash.atto.commons.node.AttoStreamRequest
import cash.atto.commons.node.AttoStreamResponse
import cash.atto.commons.node.AttoStreamResponseType
import cash.atto.commons.node.stream.server.AttoStreamMember
import cash.atto.commons.node.stream.server.AttoStreamPlan
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.HandshakeInfo
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.adapter.WebHttpHandlerBuilder
import reactor.netty.http.server.HttpServer
import reactor.netty.http.server.WebsocketServerSpec
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

@Timeout(15)
class AttoStreamWebSocketHandlerTest {
    @Test
    fun `protocol errors still close when a terminal marker is blocked behind a full data queue`() {
        // Given
        listOf(false, true).forEach { malformed ->
            val sourceFinished = CompletableFuture<Unit>()
            val source =
                flow {
                    repeat(97) { emit(JsonPrimitive(it)) }
                    sourceFinished.complete(Unit)
                }
            withServer({ _, _ -> source }) { uri ->
                withConnection(uri, autoAcknowledge = false) { socket, listener ->
                    // When
                    socket.sendText(request(), true).join()
                    repeat(32) { listener.next(AttoStreamResponseType.DATA) }
                    sourceFinished.get(5, TimeUnit.SECONDS)
                    listener.next(AttoStreamResponseType.HEARTBEAT)
                    listener.next(AttoStreamResponseType.HEARTBEAT)
                    socket.sendText(if (malformed) "{" else request(), true).join()
                    listener.closed.get(5, TimeUnit.SECONDS)

                    // Then
                    assertEquals(AttoStreamResponseType.ERROR, listener.messages.last().type)
                    assertEquals(400, listener.messages.last().status)
                    assertTrue(listener.messages.last().terminal)
                }
            }
        }
    }

    @Test
    fun `membership controls bypass exhausted credits and removed queued records never follow updated`() {
        // Given
        withPlanServer({ request, _, _ ->
            AttoStreamPlan(
                request.body!!.jsonObject.mapValues { (key, _) ->
                    AttoStreamMember(key) { flow { repeat(100) { emit(JsonPrimitive("$key:$it")) } } }
                },
            )
        }) { uri ->
            withConnection(uri, autoAcknowledge = false) { socket, listener ->
                // When
                socket.sendText(membership(AttoStreamCommandType.SUBSCRIBE, mapOf("a" to "a")), true).join()
                repeat(AttoStreamProtocol.CREDIT_WINDOW) { listener.next(AttoStreamResponseType.DATA) }
                socket.sendText(membership(AttoStreamCommandType.UPDATE, mapOf("b" to "b")), true).join()
                listener.next(AttoStreamResponseType.UPDATED)
                listener.next(AttoStreamResponseType.HEARTBEAT)

                // Then
                assertFalse(listener.messages.any { it.type == AttoStreamResponseType.DATA })

                // When
                socket.sendText(ack(AttoStreamProtocol.CREDIT_WINDOW.toString()), true).join()

                // Then
                assertEquals(JsonPrimitive("b:0"), listener.next(AttoStreamResponseType.DATA).data)
                assertFalse(
                    listener.messages.any {
                        it.data
                            ?.jsonPrimitive
                            ?.content
                            ?.startsWith("a:") == true
                    },
                )
            }
        }
    }

    @Test
    fun `source error follows accepted records and their final acknowledgement`() {
        // Given
        val source =
            flow {
                repeat(50) { emit(JsonPrimitive(it)) }
                throw IllegalStateException("source failed")
            }
        withServer({ _, _ -> source }) { uri ->
            withConnection(uri, autoAcknowledge = false) { socket, listener ->
                // When
                socket.sendText(request(), true).join()
                val records = mutableListOf<AttoStreamResponse>()
                repeat(32) { records.add(listener.next(AttoStreamResponseType.DATA)) }
                listener.next(AttoStreamResponseType.HEARTBEAT)

                // Then
                assertFalse(listener.messages.any { it.type == AttoStreamResponseType.ERROR })

                // When
                socket.sendText(ack("32"), true).join()
                repeat(18) { records.add(listener.next(AttoStreamResponseType.DATA)) }
                listener.next(AttoStreamResponseType.HEARTBEAT)

                // Then
                assertFalse(listener.closed.isDone)
                assertEquals((0 until 50).map { JsonPrimitive(it) }, records.map { it.data })

                // When
                socket.sendText(ack("50"), true).join()
                listener.closed.get(5, TimeUnit.SECONDS)

                // Then
                assertEquals(AttoStreamResponseType.ERROR, listener.messages.last().type)
                assertEquals(500, listener.messages.last().status)
                assertFalse(listener.messages.any { it.type == AttoStreamResponseType.COMPLETE })
            }
        }
    }

    @Test
    fun `future acknowledgement is a visible protocol error`() {
        // Given
        withServer({ _, _ -> flowOf(JsonPrimitive(1)) }) { uri ->
            withConnection(uri, autoAcknowledge = false) { socket, listener ->
                // When
                socket.sendText(request(), true).join()
                listener.next(AttoStreamResponseType.DATA)
                socket.sendText(ack("2"), true).join()
                listener.closed.get(5, TimeUnit.SECONDS)

                // Then
                assertEquals(AttoStreamResponseType.ERROR, listener.messages.last().type)
                assertEquals(400, listener.messages.last().status)
            }
        }
    }

    @Test
    fun `mutable stream retains existing subscriptions adds snapshots and removes only requested members`() {
        // Given
        val starts = ConcurrentHashMap<String, AtomicInteger>()
        val stops = ConcurrentHashMap<String, AtomicInteger>()
        val events = ConcurrentHashMap<String, Channel<JsonElement>>()
        val planner: suspend (AttoStreamRequest, HandshakeInfo, Boolean) -> AttoStreamPlan = { request, _, _ ->
            val definitions = request.body!!.jsonObject
            if (definitions.values.any {
                    it.jsonPrimitive.content == "invalid"
                }
            ) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid membership")
            }
            AttoStreamPlan(
                definitions.mapValues { (key, signature) ->
                    AttoStreamMember(signature.jsonPrimitive.content) {
                        flow {
                            starts.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
                            try {
                                emit(JsonPrimitive("$key:snapshot"))
                                events.computeIfAbsent(key) { Channel(8) }.receiveAsFlow().collect { emit(it) }
                            } finally {
                                stops.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
                            }
                        }
                    }
                },
            )
        }
        withPlanServer(planner) { uri ->
            withConnection(uri) { socket, listener ->
                // When
                socket.sendText(membership(AttoStreamCommandType.SUBSCRIBE, emptyMap()), true).join()
                listener.next(AttoStreamResponseType.SUBSCRIBED)
                listener.next(AttoStreamResponseType.HEARTBEAT)
                socket.sendText(membership(AttoStreamCommandType.UPDATE, mapOf("a" to "range1")), true).join()
                listener.next(AttoStreamResponseType.UPDATED)
                assertEquals(JsonPrimitive("a:snapshot"), listener.next(AttoStreamResponseType.DATA).data)
                socket.sendText(membership(AttoStreamCommandType.UPDATE, mapOf("a" to "range1", "b" to "range2")), true).join()
                listener.next(AttoStreamResponseType.UPDATED)
                assertEquals(JsonPrimitive("b:snapshot"), listener.next(AttoStreamResponseType.DATA).data)
                events.getValue("a").trySend(JsonPrimitive("a:live"))
                assertEquals(JsonPrimitive("a:live"), listener.next(AttoStreamResponseType.DATA).data)

                // Then
                assertEquals(1, starts.getValue("a").get())
                assertEquals(1, starts.getValue("b").get())

                // When
                socket.sendText(membership(AttoStreamCommandType.UPDATE, mapOf("a" to "changed", "b" to "range2")), true).join()
                val changed = listener.next(AttoStreamResponseType.ERROR)
                socket.sendText(membership(AttoStreamCommandType.UPDATE, mapOf("a" to "range1", "c" to "invalid")), true).join()
                val invalid = listener.next(AttoStreamResponseType.ERROR)
                events.getValue("a").trySend(JsonPrimitive("a:still-live"))

                // Then
                assertEquals(400, changed.status)
                assertFalse(changed.terminal)
                assertEquals(400, invalid.status)
                assertFalse(invalid.terminal)
                assertEquals(JsonPrimitive("a:still-live"), listener.next(AttoStreamResponseType.DATA).data)
                assertEquals(1, starts.getValue("a").get())
                assertFalse(stops.containsKey("a"))

                // When
                socket.sendText(membership(AttoStreamCommandType.UPDATE, mapOf("b" to "range2")), true).join()
                listener.next(AttoStreamResponseType.UPDATED)
                events.getValue("b").trySend(JsonPrimitive("b:retained"))

                // Then
                assertEquals(JsonPrimitive("b:retained"), listener.next(AttoStreamResponseType.DATA).data)
                assertEquals(1, stops.getValue("a").get())
                assertEquals(1, starts.getValue("b").get())
            }
        }
    }

    @Test
    fun `credit window bounds slow consumers and completion waits for the last acknowledgement`() {
        // Given
        val produced = AtomicInteger()
        val source =
            flow {
                repeat(200) {
                    produced.incrementAndGet()
                    emit(JsonPrimitive(it))
                }
            }
        withServer({ _, _ -> source }) { uri ->
            withConnection(uri, autoAcknowledge = false) { socket, listener ->
                // When
                socket.sendText(request(), true).join()
                listener.next(AttoStreamResponseType.SUBSCRIBED)
                val records = mutableListOf<AttoStreamResponse>()
                repeat(AttoStreamProtocol.CREDIT_WINDOW) { records.add(listener.next(AttoStreamResponseType.DATA)) }
                listener.next(AttoStreamResponseType.HEARTBEAT)
                listener.next(AttoStreamResponseType.HEARTBEAT)

                // Then
                assertTrue(produced.get() <= AttoStreamProtocol.CREDIT_WINDOW + 66)
                assertFalse(listener.messages.any { it.type == AttoStreamResponseType.DATA || it.type == AttoStreamResponseType.COMPLETE })

                // When
                while (records.size < 200) {
                    socket.sendText(ack(records.last().sequence!!), true).join()
                    repeat(
                        minOf(AttoStreamProtocol.CREDIT_WINDOW, 200 - records.size),
                    ) { records.add(listener.next(AttoStreamResponseType.DATA)) }
                }

                // Then
                assertFalse(listener.closed.isDone)
                assertEquals((0 until 200).map { JsonPrimitive(it) }, records.map { it.data })
                assertEquals((1..200).map { it.toString() }, records.map { it.sequence })

                // When
                socket.sendText(ack(records.last().sequence!!), true).join()
                listener.closed.get(5, TimeUnit.SECONDS)

                // Then
                assertEquals(AttoStreamResponseType.COMPLETE, listener.messages.last().type)
            }
        }
    }

    @Test
    fun `sealed mutable stream completes only after finished members are acknowledged`() {
        // Given
        withPlanServer({ request, _, _ ->
            AttoStreamPlan(request.body!!.jsonObject.mapValues { (key, _) -> AttoStreamMember(key) { flowOf(JsonPrimitive(key)) } })
        }) { uri ->
            withConnection(uri) { socket, listener ->
                // When
                socket.sendText(membership(AttoStreamCommandType.SUBSCRIBE, mapOf("a" to "a")), true).join()
                listener.next(AttoStreamResponseType.DATA)
                listener.next(AttoStreamResponseType.HEARTBEAT)
                socket.sendText(membership(AttoStreamCommandType.UPDATE, mapOf("a" to "a", "b" to "b")), true).join()
                listener.next(AttoStreamResponseType.UPDATED)

                // Then
                assertEquals(JsonPrimitive("b"), listener.next(AttoStreamResponseType.DATA).data)

                // When
                socket.sendText(AttoStreamProtocol.encodeCommand(AttoStreamCommand(AttoStreamCommandType.SEAL)), true).join()
                listener.closed.get(5, TimeUnit.SECONDS)

                // Then
                assertEquals(AttoStreamResponseType.COMPLETE, listener.messages.last().type)
            }
        }
    }

    private fun membership(
        type: AttoStreamCommandType,
        members: Map<String, String>,
    ): String =
        AttoStreamProtocol.encodeCommand(
            AttoStreamCommand(
                type,
                AttoStreamRequest(AttoStreamMethod.POST, "/v2/accounts/stream", JsonObject(members.mapValues { JsonPrimitive(it.value) })),
                mutable = type == AttoStreamCommandType.SUBSCRIBE,
            ),
        )

    private fun ack(sequence: String): String =
        AttoStreamProtocol.encodeCommand(AttoStreamCommand(AttoStreamCommandType.ACK, sequence = sequence))

    @Test
    fun `heartbeats begin while dispatcher is suspended before returning its flow`() {
        // Given
        withServer({ _, _ ->
            delay(180)
            flowOf(JsonPrimitive("ready"))
        }) { uri ->
            withConnection(uri) { socket, listener ->
                // When
                socket.sendText(request(), true).join()
                listener.closed.get(5, TimeUnit.SECONDS)
                val responses = listener.messages.toList()

                // Then
                assertTrue(responses.count { it.type == AttoStreamResponseType.HEARTBEAT } >= 2)
                assertEquals(JsonPrimitive("ready"), responses.first { it.type == AttoStreamResponseType.DATA }.data)
                assertEquals(AttoStreamResponseType.COMPLETE, responses.last().type)
            }
        }
    }

    @Test
    fun `real connection heartbeats once upstream subscription starts and flushes ordered data then complete`() {
        // Given
        val subscriptions = AtomicInteger()
        val source =
            flow {
                subscriptions.incrementAndGet()
                delay(180)
                repeat(100) { emit(JsonPrimitive(it)) }
            }
        withServer({ _, _ -> source }) { uri ->
            withConnection(uri) { socket, listener ->
                // When
                socket.sendText(request(), true).join()
                listener.closed.get(5, TimeUnit.SECONDS)
                val responses = listener.messages.toList()

                // Then
                assertEquals(1, subscriptions.get())
                assertTrue(responses.count { it.type == AttoStreamResponseType.HEARTBEAT } >= 2)
                assertEquals(
                    (0 until 100).map { JsonPrimitive(it) },
                    responses.filter { it.type == AttoStreamResponseType.DATA }.map { it.data },
                )
                assertEquals(AttoStreamResponseType.COMPLETE, responses.last().type)
                assertEquals(1, responses.count { it.type == AttoStreamResponseType.COMPLETE })
            }
        }
    }

    @Test
    fun `empty operation completes without inventing a record`() {
        // Given
        withServer({ _, _ -> emptyFlow() }) { uri ->
            withConnection(uri) { socket, listener ->
                // When
                socket.sendText(request(), true).join()
                listener.closed.get(5, TimeUnit.SECONDS)

                // Then
                assertEquals(listOf(AttoStreamResponseType.SUBSCRIBED, AttoStreamResponseType.COMPLETE), listener.messages.map { it.type })
            }
        }
    }

    @Test
    fun `dispatcher and collection errors emit one error without completion`() {
        // Given
        val dispatchers: List<suspend (AttoStreamRequest, HandshakeInfo) -> Flow<JsonElement>> =
            listOf(
                { _, _ -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid range") },
                { _, _ ->
                    flow {
                        emit(JsonPrimitive(1))
                        throw IllegalStateException("private internal detail")
                    }
                },
            )
        dispatchers.forEachIndexed { index, dispatcher ->
            withServer(dispatcher) { uri ->
                withConnection(uri) { socket, listener ->
                    // When
                    socket.sendText(request(), true).join()
                    listener.closed.get(5, TimeUnit.SECONDS)
                    val responses = listener.messages.toList()

                    // Then
                    assertEquals(if (index == 0) 400 else 500, responses.last().status)
                    assertEquals(AttoStreamResponseType.ERROR, responses.last().type)
                    assertEquals(1, responses.count { it.type == AttoStreamResponseType.ERROR })
                    assertFalse(responses.any { it.type == AttoStreamResponseType.COMPLETE })
                    assertFalse(
                        responses
                            .last()
                            .message
                            .orEmpty()
                            .contains("private internal detail"),
                    )
                }
            }
        }
    }

    @Test
    fun `Spring rejection status and reason cross the stream adapter`() {
        // Given
        listOf("Range not available", null).forEach { reason ->
            withPlanServer({ _, handshake, _ ->
                assertEquals(AttoStreamProtocol.PATH, handshake.uri.path)
                throw ResponseStatusException(HttpStatus.FORBIDDEN, reason)
            }) { uri ->
                withConnection(uri) { socket, listener ->
                    // When
                    socket.sendText(request(), true).join()
                    listener.closed.get(5, TimeUnit.SECONDS)
                    val responses = listener.messages.toList()

                    // Then
                    assertEquals(AttoStreamResponseType.ERROR, responses.last().type)
                    assertEquals(403, responses.last().status)
                    assertEquals(reason ?: "Stream request failed", responses.last().message)
                    assertTrue(responses.last().terminal)
                    assertEquals(1, responses.count { it.type == AttoStreamResponseType.ERROR })
                    assertFalse(responses.any { it.type == AttoStreamResponseType.COMPLETE })
                }
            }
        }
    }

    @Test
    fun `operation timeout is an error while heartbeats keep arriving`() {
        // Given
        withServer({ _, _ -> flow { withTimeout(180) { awaitCancellation() } } }) { uri ->
            withConnection(uri) { socket, listener ->
                // When
                socket.sendText(request(), true).join()
                listener.closed.get(5, TimeUnit.SECONDS)
                val responses = listener.messages.toList()

                // Then
                assertTrue(responses.any { it.type == AttoStreamResponseType.HEARTBEAT })
                assertEquals(504, responses.last().status)
                assertFalse(responses.any { it.type == AttoStreamResponseType.COMPLETE })
            }
        }
    }

    @Test
    fun `disconnect cancels upstream and an extra request terminates it with an error`() {
        // Given
        val cancellations = AtomicInteger()
        val source =
            flow<JsonElement> {
                try {
                    emit(JsonPrimitive("ready"))
                    awaitCancellation()
                } finally {
                    cancellations.incrementAndGet()
                }
            }
        withServer({ _, _ -> source }) { uri ->
            withConnection(uri) { socket, listener ->
                // When
                socket.sendText(request(), true).join()
                listener.next(AttoStreamResponseType.DATA)
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join()
                listener.closed.get(5, TimeUnit.SECONDS)

                // Then
                await { cancellations.get() == 1 }
            }
            withConnection(uri) { socket, listener ->
                // When
                socket.sendText(request(), true).join()
                listener.next(AttoStreamResponseType.DATA)
                socket.sendText(request(), true).join()
                listener.closed.get(5, TimeUnit.SECONDS)

                // Then
                assertEquals(400, listener.messages.last().status)
                assertFalse(listener.messages.any { it.type == AttoStreamResponseType.COMPLETE })
                await { cancellations.get() == 2 }
            }
        }
    }

    @Test
    fun `malformed text and binary requests never invoke the dispatcher`() {
        // Given
        val calls = AtomicInteger()
        withServer({ _, _ ->
            calls.incrementAndGet()
            flowOf(JsonPrimitive(1))
        }) { uri ->
            listOf(false, true).forEach { binary ->
                withConnection(uri) { socket, listener ->
                    // When
                    if (binary) socket.sendBinary(ByteBuffer.wrap(byteArrayOf(1, 2)), true).join() else socket.sendText("{", true).join()
                    listener.closed.get(5, TimeUnit.SECONDS)

                    // Then
                    assertEquals(400, listener.messages.last().status)
                    assertEquals(0, calls.get())
                }
            }
        }
    }

    private fun request(): String =
        AttoStreamProtocol.encodeCommand(
            AttoStreamCommand(AttoStreamCommandType.SUBSCRIBE, AttoStreamRequest(AttoStreamMethod.GET, "/v2/accounts/stream")),
        )

    private fun withServer(
        dispatcher: suspend (AttoStreamRequest, HandshakeInfo) -> Flow<JsonElement>,
        block: (URI) -> Unit,
    ) = withPlanServer({ request, handshake, _ ->
        AttoStreamPlan(mapOf("stream" to AttoStreamMember("static") { dispatcher(request, handshake) }))
    }, block)

    private fun withPlanServer(
        planner: suspend (AttoStreamRequest, HandshakeInfo, Boolean) -> AttoStreamPlan,
        block: (URI) -> Unit,
    ) {
        AnnotationConfigApplicationContext().use { context ->
            context.registerBean(
                AttoStreamWebSocketHandler::class.java,
                Supplier { AttoStreamWebSocketHandler(planner, Duration.ofMillis(50)) },
            )
            context.register(TestConfiguration::class.java)
            context.refresh()
            val adapter = ReactorHttpHandlerAdapter(WebHttpHandlerBuilder.applicationContext(context).build())
            val server =
                HttpServer
                    .create()
                    .host("127.0.0.1")
                    .port(0)
                    .handle(adapter)
                    .bindNow()
            try {
                block(URI("ws://127.0.0.1:${server.port()}${AttoStreamProtocol.PATH}"))
            } finally {
                server.disposeNow()
            }
        }
    }

    private fun withConnection(
        uri: URI,
        autoAcknowledge: Boolean = true,
        block: (WebSocket, ResponseListener) -> Unit,
    ) {
        HttpClient.newHttpClient().use { client ->
            val listener = ResponseListener(autoAcknowledge)
            val socket = client.newWebSocketBuilder().buildAsync(uri, listener).get(5, TimeUnit.SECONDS)
            try {
                block(socket, listener)
            } finally {
                socket.abort()
            }
        }
    }

    private fun await(predicate: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!predicate() && System.nanoTime() < end) Thread.sleep(5)
        assertTrue(predicate())
    }

    private class ResponseListener(
        private val autoAcknowledge: Boolean,
    ) : WebSocket.Listener {
        val messages = LinkedBlockingQueue<AttoStreamResponse>()
        val closed = CompletableFuture<Int>()
        private val text = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(
            webSocket: WebSocket,
            data: CharSequence,
            last: Boolean,
        ): CompletionStage<*>? {
            text.append(data)
            if (last) {
                val response = AttoStreamProtocol.decodeResponse(text.toString())
                if (autoAcknowledge && response.type == AttoStreamResponseType.DATA) {
                    webSocket
                        .sendText(
                            AttoStreamProtocol.encodeCommand(AttoStreamCommand(AttoStreamCommandType.ACK, sequence = response.sequence)),
                            true,
                        ).join()
                }
                messages.add(response)
                text.setLength(0)
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(
            webSocket: WebSocket,
            statusCode: Int,
            reason: String,
        ): CompletionStage<*>? {
            closed.complete(statusCode)
            return null
        }

        override fun onError(
            webSocket: WebSocket,
            error: Throwable,
        ) {
            closed.completeExceptionally(error)
        }

        fun next(type: AttoStreamResponseType): AttoStreamResponse {
            while (true) {
                val value = messages.poll(5, TimeUnit.SECONDS) ?: error("No $type response")
                if (value.type == type) return value
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    private class TestConfiguration {
        @Bean
        fun mapping(handler: AttoStreamWebSocketHandler): SimpleUrlHandlerMapping =
            SimpleUrlHandlerMapping(mapOf(AttoStreamProtocol.PATH to handler), -1)

        @Bean
        fun socketAdapter(): WebSocketHandlerAdapter =
            WebSocketHandlerAdapter(
                HandshakeWebSocketService(
                    ReactorNettyRequestUpgradeStrategy {
                        WebsocketServerSpec.builder().maxFramePayloadLength(AttoStreamWebSocketHandler.MAX_FRAME_PAYLOAD_LENGTH)
                    },
                ),
            ).apply { order = 1 }
    }
}
