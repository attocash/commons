package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlockType
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoSendBlock
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoWork
import cash.atto.commons.node.monitor.createAccountMonitor
import cash.atto.commons.node.monitor.toTransactionMonitor
import cash.atto.commons.toAttoVersion
import cash.atto.commons.transport.AttoWebSocketTransport
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AttoNodeClientRemoteWebSocketTest {
    private val address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32) { 1 }))
    private val receiver = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32) { 2 }))
    private val hash = AttoHash(ByteArray(32) { 3 })
    private val transaction =
        AttoTransaction(
            AttoSendBlock(
                AttoNetwork.LOCAL,
                0U.toAttoVersion(),
                address,
                AttoHeight(2UL),
                AttoAmount(7UL),
                AttoInstant.fromEpochMilliseconds(1_700_000_000_000),
                hash,
                receiver,
                AttoAmount(1UL),
            ),
            AttoSignature(ByteArray(64)),
            AttoWork(ByteArray(8)),
        )

    @Test
    fun `all read streaming forms use sockets while publication and scalar functions use HTTP`(): Unit =
        runBlocking {
            // Given
            val requests = CopyOnWriteArrayList<AttoStreamRequest>()
            val http = CopyOnWriteArrayList<String>()
            withServer(http, handler = { request ->
                requests += request
                sendResponse(AttoStreamResponse(AttoStreamResponseType.COMPLETE))
            }) { client ->
                val search = HeightSearch(listOf(AccountHeightSearch(address, AttoHeight(2UL), AttoHeight(5UL))))
                // When
                client.accountStream().toList()
                client.accountStream(address).toList()
                client.accountStream(listOf(address)).toList()
                client.receivableStream(address, AttoAmount(7UL)).toList()
                client.receivableStream(listOf(address), AttoAmount(7UL)).toList()
                assertFails { client.accountEntry(hash) }
                client.accountEntryStream().toList()
                client.accountEntryStream(hash).toList()
                client.accountEntryStream(address, AttoHeight(2UL), AttoHeight(5UL)).toList()
                client.accountEntryStream(search).toList()
                client.transactionStream().toList()
                client.transactionStream(hash).toList()
                client.transactionStream(address, AttoHeight(2UL), AttoHeight(5UL)).toList()
                client.transactionStream(search).toList()
                client.publish(transaction)
                assertNull(client.account(address))
                assertFails { client.account(listOf(address)) }
                assertFails { client.transaction(hash) }
                assertFails { client.now(AttoInstant.fromEpochMilliseconds(1)) }
                assertFails { client.voterWeight(address) }
                // Then
                val encoded = address.toString().encodeURLParameter()
                assertEquals(
                    listOf(
                        "GET /v2/accounts/stream",
                        "GET /v2/accounts/$encoded/stream",
                        "POST /v2/accounts/stream",
                        "GET /v2/accounts/$encoded/receivables/stream?minAmount=7",
                        "POST /v2/accounts/receivables/stream?minAmount=7",
                        "GET /v2/accounts/entries/$hash/stream",
                        "GET /v2/accounts/entries/stream",
                        "GET /v2/accounts/entries/$hash/stream",
                        "GET /v2/accounts/$encoded/entries/stream?fromHeight=2&toHeight=5",
                        "POST /v2/accounts/entries/stream",
                        "GET /v2/transactions/stream",
                        "GET /v2/transactions/$hash/stream",
                        "GET /v2/accounts/$encoded/transactions/stream?fromHeight=2&toHeight=5",
                        "POST /v2/accounts/transactions/stream",
                    ),
                    requests.map { "${it.method} ${it.path}" },
                )
                assertEquals(Json.parseToJsonElement("""{"addresses":["$address"]}"""), requests[2].body)
                assertEquals(
                    listOf(
                        "POST /api/v2/transactions",
                        "GET /api/v2/accounts/$encoded",
                        "POST /api/v2/accounts",
                        "GET /api/transactions/$hash",
                        "GET /api/instants/${AttoInstant.fromEpochMilliseconds(1)}",
                        "GET /api/vote-weights/${address.path}",
                    ),
                    http.toList(),
                )
            }
        }

    @Test
    fun `canonical data and heartbeats preserve entry first cancellation`(): Unit =
        runBlocking {
            // Given
            val entry =
                AttoAccountEntry(
                    hash,
                    address,
                    AttoHeight(2UL),
                    AttoBlockType.SEND,
                    receiver,
                    AttoAmount(8UL),
                    AttoAmount(7UL),
                    AttoInstant.fromEpochMilliseconds(1_700_000_000_000),
                )
            val cancelled = CompletableDeferred<Unit>()
            withServer(handler = {
                try {
                    sendResponse(AttoStreamResponse(AttoStreamResponseType.HEARTBEAT))
                    sendResponse(AttoStreamResponse(AttoStreamResponseType.DATA, Json.parseToJsonElement(entry.toJson())))
                    incoming.receiveCatching()
                } finally {
                    cancelled.complete(Unit)
                }
            }) { client ->
                // When
                val actual = client.accountEntry(hash)
                // Then
                assertEquals(entry, actual)
                withTimeout(5.seconds) { cancelled.await() }
            }
        }

    @Test
    fun `only explicit completion succeeds and malformed binary oversized frames fail`(): Unit =
        runBlocking {
            // Given
            val messages =
                listOf<String?>(
                    null,
                    "not JSON",
                    """{"type":"data"}""",
                    AttoStreamProtocol.encodeResponse(AttoStreamResponse(AttoStreamResponseType.DATA, JsonPrimitive("x".repeat(2001)))),
                )
            for (message in messages) {
                withServer(handler = {
                    if (message !=
                        null
                    ) {
                        send(Frame.Text(message))
                    }
                    ; close(CloseReason(CloseReason.Codes.NORMAL, "done"))
                }) { client ->
                    // When
                    val failure = runCatching { client.transactionStream().toList() }.exceptionOrNull()
                    // Then
                    assertNotNull(failure, "Unexpected successful completion for $message")
                }
            }
            withServer(handler = {
                send(Frame.Binary(true, byteArrayOf(1)))
                close()
            }) { client ->
                assertFails { client.transactionStream().toList() }
            }
        }

    @Test
    fun `server stream errors propagate`(): Unit =
        runBlocking {
            // Given
            withServer(
                handler = { sendResponse(AttoStreamResponse(AttoStreamResponseType.ERROR, status = 429, message = "busy")) },
            ) { client ->
                // When
                val failure = assertFailsWith<AttoNodeStreamException> { client.accountStream().toList() }
                // Then
                assertEquals(429, failure.status)
                assertEquals("busy", failure.message)
            }
        }

    @Test
    fun `monitor membership updates retain one socket through empty remove and readd`(): Unit =
        runBlocking {
            // Given
            val updated = Channel<Unit>(8)
            val received = Channel<AttoAccount>(8)
            val closed = CompletableDeferred<Unit>()
            var connections = 0
            val snapshots = mutableListOf<AttoAddress>()
            withServer(handler = { initial ->
                connections++
                var members = emptySet<AttoAddress>()
                var sequence = 0

                suspend fun apply(request: AttoStreamRequest) {
                    val next =
                        request.body!!
                            .jsonObject
                            .getValue(
                                "addresses",
                            ).jsonArray
                            .map { AttoAddress.parse(it.jsonPrimitive.content) }
                            .toSet()
                    for (member in next - members) {
                        snapshots += member
                        val account =
                            AttoAccount(
                                member,
                                AttoNetwork.LOCAL,
                                0U.toAttoVersion(),
                                AttoHeight(2UL),
                                AttoAmount(7UL),
                                hash,
                                AttoInstant.fromEpochMilliseconds(1_700_000_000_000),
                                receiver,
                            )
                        send(
                            Frame.Text(
                                AttoStreamProtocol.encodeResponse(
                                    AttoStreamResponse(
                                        AttoStreamResponseType.DATA,
                                        Json.parseToJsonElement(account.toJson()),
                                        sequence = (++sequence).toString(),
                                    ),
                                ),
                            ),
                        )
                    }
                    members = next
                    sendResponse(AttoStreamResponse(AttoStreamResponseType.UPDATED))
                    updated.send(Unit)
                }
                try {
                    apply(initial)
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val command = AttoStreamProtocol.decodeCommand(frame.readText())
                        if (command.type == AttoStreamCommandType.UPDATE) apply(requireNotNull(command.request))
                    }
                } finally {
                    closed.complete(Unit)
                }
            }) { client ->
                coroutineScope {
                    val monitor = client.createAccountMonitor()
                    val collector = launch { monitor.accountStream().collect { received.send(it) } }
                    updated.receive()
                    // When
                    monitor.monitor(address)
                    updated.receive()
                    assertEquals(address, received.receive().address)
                    monitor.monitor(receiver)
                    updated.receive()
                    assertEquals(receiver, received.receive().address)
                    monitor.stopMonitoring(address)
                    updated.receive()
                    monitor.stopMonitoring(receiver)
                    updated.receive()
                    monitor.monitor(address)
                    updated.receive()
                    assertEquals(address, received.receive().address)
                    collector.cancelAndJoin()
                    // Then
                    withTimeout(5.seconds) { closed.await() }
                    assertEquals(1, connections)
                    assertEquals(listOf(address, receiver, address), snapshots)
                }
            }
        }

    @Test
    fun `slow consumer receives over sixty four records ordered with credits and heartbeats`(): Unit =
        runBlocking {
            // Given
            val expected = (2UL..101UL).toList()
            withServer(handler = {
                coroutineScope {
                    val credits = Semaphore(AttoStreamProtocol.CREDIT_WINDOW)
                    val producer =
                        launch {
                            for ((index, height) in expected.withIndex()) {
                                credits.acquire()
                                val value = transaction.copy(block = (transaction.block as AttoSendBlock).copy(height = AttoHeight(height)))
                                send(
                                    Frame.Text(
                                        AttoStreamProtocol.encodeResponse(
                                            AttoStreamResponse(
                                                AttoStreamResponseType.DATA,
                                                Json.parseToJsonElement(value.toJson()),
                                                sequence = (index + 1).toString(),
                                            ),
                                        ),
                                    ),
                                )
                                sendResponse(AttoStreamResponse(AttoStreamResponseType.HEARTBEAT))
                            }
                        }
                    var acknowledged = 0
                    while (acknowledged < expected.size) {
                        val command = AttoStreamProtocol.decodeCommand((incoming.receive() as Frame.Text).readText())
                        if (command.type == AttoStreamCommandType.ACK) {
                            val next = requireNotNull(command.sequence).toInt()
                            repeat(next - acknowledged) { credits.release() }
                            acknowledged = next
                        }
                    }
                    producer.join()
                    sendResponse(AttoStreamResponse(AttoStreamResponseType.COMPLETE))
                }
            }) { client ->
                // When
                val actual = mutableListOf<ULong>()
                client.transactionStream(flowOf(HeightSearch(listOf(AccountHeightSearch(address, AttoHeight(2UL)))))).collect {
                    delay(2)
                    actual += it.block.height.value
                }
                // Then
                assertEquals(expected, actual)
            }
        }

    @Test
    fun `height monitor freezes retained ranges and retries from acknowledged cursor`(): Unit =
        runBlocking {
            // Given
            val received = Channel<Unit>(1)
            val retried = CompletableDeferred<HeightSearch>()
            val updated = CompletableDeferred<HeightSearch>()
            var connections = 0
            withServer(handler = { request ->
                connections++
                val search = Json.decodeFromJsonElement(HeightSearch.serializer(), requireNotNull(request.body))
                if (connections == 1) {
                    val value = transaction.copy(block = (transaction.block as AttoSendBlock).copy(height = AttoHeight(5UL)))
                    sendResponse(AttoStreamResponse(AttoStreamResponseType.DATA, Json.parseToJsonElement(value.toJson())))
                    while (true) {
                        val command = AttoStreamProtocol.decodeCommand((incoming.receive() as Frame.Text).readText())
                        if (command.type == AttoStreamCommandType.UPDATE) {
                            updated.complete(Json.decodeFromJsonElement(HeightSearch.serializer(), requireNotNull(command.request?.body)))
                            sendResponse(AttoStreamResponse(AttoStreamResponseType.ERROR, status = 503, message = "retry"))
                            break
                        }
                    }
                } else {
                    retried.complete(search)
                    incoming.receiveCatching()
                }
            }) { client ->
                coroutineScope {
                    val monitor = client.createAccountMonitor()
                    monitor.monitor(address)
                    val collector =
                        launch {
                            monitor.toTransactionMonitor().stream().collect { message ->
                                message.acknowledge()
                                received.send(Unit)
                            }
                        }
                    received.receive()
                    // When
                    monitor.monitor(receiver)
                    val inPlace = updated.await()
                    val afterRetry = retried.await()
                    collector.cancelAndJoin()
                    // Then
                    assertEquals(AttoHeight.MIN, inPlace.search.single { it.address == address }.fromHeight)
                    assertEquals(AttoHeight(5UL), afterRetry.search.single { it.address == address }.fromHeight)
                    assertEquals(2, connections)
                }
            }
        }

    @Test
    fun `explicit cancellation and overall deadline release the real socket despite heartbeats`(): Unit =
        runBlocking {
            // Given
            val connected = CompletableDeferred<Unit>()
            val closed = CompletableDeferred<Unit>()
            withServer(handler = {
                try {
                    connected.complete(Unit)
                    incoming.receiveCatching()
                } finally {
                    closed.complete(Unit)
                }
            }) { client ->
                coroutineScope {
                    val collector = launch { client.accountStream().collect {} }
                    connected.await()
                    // When
                    collector.cancelAndJoin()
                    // Then
                    withTimeout(5.seconds) { closed.await() }
                }
            }
            val timedOutClosed = CompletableDeferred<Unit>()
            var baseUrl = ""
            withServer(onBaseUrl = { baseUrl = it }, handler = {
                try {
                    while (true) {
                        sendResponse(AttoStreamResponse(AttoStreamResponseType.HEARTBEAT))
                        delay(5)
                    }
                } finally {
                    timedOutClosed.complete(Unit)
                }
            }) {
                // Given
                val streams = AttoNodeWebSocketStreams(AttoWebSocketTransport(baseUrl), Json)
                // When
                val failure =
                    assertFailsWith<IllegalStateException> {
                        streams
                            .stream(
                                AttoTransaction.serializer(),
                                flowOf(AttoStreamRequest(AttoStreamMethod.GET, "/v2/transactions/stream")),
                                false,
                                requestTimeout = 100.milliseconds,
                            ).toList()
                    }
                // Then
                assertEquals("WebSocket stream request timeout", failure.message)
                withTimeout(5.seconds) { timedOutClosed.await() }
            }
        }

    @Test
    fun `fragmented JSON text records are reassembled`(): Unit =
        runBlocking {
            // Given
            val response =
                AttoStreamProtocol
                    .encodeResponse(
                        AttoStreamResponse(
                            AttoStreamResponseType.DATA,
                            Json.parseToJsonElement(transaction.toJson()),
                            sequence = "1",
                        ),
                    ).toByteArray()
            withServer(handler = {
                send(Frame.Text(false, response.copyOfRange(0, response.size / 2)))
                send(Frame.Text(true, response.copyOfRange(response.size / 2, response.size)))
                incoming.receiveCatching()
            }) { client ->
                // When
                val actual = client.transactionStream().first()
                // Then
                assertEquals(transaction, actual)
            }
        }

    @Test
    fun `buffered terminal frame stays ahead of an immediate normal socket close`(): Unit =
        runBlocking {
            // Given
            var baseUrl = ""
            withServer(onBaseUrl = { baseUrl = it }, handler = {
                sendResponse(AttoStreamResponse(AttoStreamResponseType.HEARTBEAT))
                sendResponse(AttoStreamResponse(AttoStreamResponseType.COMPLETE))
                close(CloseReason(CloseReason.Codes.NORMAL, "done"))
            }) {
                val subscribe =
                    AttoStreamProtocol.encodeCommand(
                        AttoStreamCommand(
                            AttoStreamCommandType.SUBSCRIBE,
                            AttoStreamRequest(AttoStreamMethod.GET, "/v2/accounts/stream"),
                        ),
                    )
                // When
                val frames =
                    AttoWebSocketTransport(baseUrl)
                        .exchange(AttoStreamProtocol.PATH, flowOf(subscribe), 5.seconds, 5.seconds)
                        .onEach { delay(50) }
                        .take(2)
                        .toList()
                // Then
                assertEquals(
                    listOf(AttoStreamResponseType.HEARTBEAT, AttoStreamResponseType.COMPLETE),
                    frames.map {
                        AttoStreamProtocol.decodeResponse(it).type
                    },
                )
            }
        }

    @Test
    fun `queued subscription updates cannot block a drained record acknowledgement after close`(): Unit =
        runBlocking {
            // Given
            withServer(handler = {
                sendResponse(AttoStreamResponse(AttoStreamResponseType.DATA, Json.parseToJsonElement(transaction.toJson())))
                close(CloseReason(CloseReason.Codes.NORMAL, "closed"))
            }) { client ->
                val search = HeightSearch(listOf(AccountHeightSearch(address, AttoHeight(2UL))))
                val updates =
                    flow {
                        while (true) {
                            emit(search)
                            yield()
                        }
                    }
                val received = mutableListOf<AttoTransaction>()
                // When
                val failure =
                    withTimeout(5.seconds) {
                        runCatching {
                            client.transactionStream(updates).collect {
                                received += it
                                delay(50)
                            }
                        }.exceptionOrNull()
                    }
                // Then
                assertEquals(listOf(transaction), received)
                assertNotNull(failure)
            }
        }

    @Test
    fun `membership timeout after subscribing cancels the stream and closes the socket`(): Unit =
        runBlocking {
            // Given
            val connected = CompletableDeferred<Unit>()
            val closed = CompletableDeferred<Unit>()
            val expected = CompletableDeferred<TimeoutCancellationException>()
            val memberships =
                flow {
                    emit(listOf(address))
                    connected.await()
                    try {
                        withTimeout(50.milliseconds) { awaitCancellation() }
                    } catch (failure: TimeoutCancellationException) {
                        expected.complete(failure)
                        throw failure
                    }
                }
            withServer(handler = {
                try {
                    connected.complete(Unit)
                    incoming.receiveCatching()
                } finally {
                    closed.complete(Unit)
                }
            }) { client ->
                // When
                val failure =
                    withTimeout(5.seconds) {
                        assertFailsWith<TimeoutCancellationException> { client.accountStream(memberships).collect() }
                    }

                // Then
                assertEquals(expected.await().message, failure.message)
                withTimeout(5.seconds) { closed.await() }
            }
        }

    @Test
    fun `normal completion cancels ongoing membership input without failing the stream`(): Unit =
        runBlocking {
            // Given
            val cancelled = CompletableDeferred<Unit>()
            val memberships =
                flow {
                    try {
                        emit(listOf(address))
                        awaitCancellation()
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
            withServer(handler = {
                sendResponse(AttoStreamResponse(AttoStreamResponseType.COMPLETE))
            }) { client ->
                // When
                val received = client.accountStream(memberships).toList()

                // Then
                assertEquals(emptyList(), received)
                withTimeout(5.seconds) { cancelled.await() }
            }
        }

    @Test
    fun `concurrent child cancellation never appears as empty stream completion`(): Unit =
        runBlocking {
            withContext(Dispatchers.Default) {
                withTimeout(10.seconds) {
                    repeat(100) { attempt ->
                        // Given
                        val expectedMembership = CancellationException("Membership cancelled at attempt $attempt")
                        val memberships =
                            flow<Collection<AttoAddress>> {
                                currentCoroutineContext().cancel(expectedMembership)
                            }
                        val membershipClient = AttoNodeClient.remote("http://127.0.0.1:1", AttoNodeStreamTransport.WEBSOCKET)
                        val expectedHeader = CompletableDeferred<TimeoutCancellationException>()
                        val headerClient =
                            AttoNodeClient.remote("http://127.0.0.1:1", AttoNodeStreamTransport.WEBSOCKET) {
                                try {
                                    withTimeout(1.milliseconds) { awaitCancellation() }
                                } catch (failure: TimeoutCancellationException) {
                                    expectedHeader.complete(failure)
                                    throw failure
                                }
                            }

                        // When
                        val membershipFailure =
                            assertFailsWith<CancellationException> { membershipClient.accountStream(memberships).collect() }
                        val headerFailure = assertFailsWith<TimeoutCancellationException> { headerClient.accountStream().collect() }

                        // Then
                        assertEquals(expectedMembership.message, membershipFailure.message)
                        assertEquals(expectedHeader.await().message, headerFailure.message)
                    }
                }
            }
        }

    private suspend fun withServer(
        http: MutableList<String> = CopyOnWriteArrayList(),
        handler: suspend DefaultWebSocketServerSession.(AttoStreamRequest) -> Unit,
        onBaseUrl: (String) -> Unit = {},
        block: suspend (AttoNodeClient) -> Unit,
    ) {
        val server =
            embeddedServer(CIO, host = "127.0.0.1", port = 0) {
                install(WebSockets)
                routing {
                    webSocket("/api/v2/stream") {
                        val command = AttoStreamProtocol.decodeCommand((incoming.receive() as Frame.Text).readText())
                        val request = requireNotNull(command.request)
                        handler(request)
                    }
                    route("/{path...}") {
                        handle {
                            http += "${call.request.httpMethod.value} ${call.request.uri}"
                            if (call.request.httpMethod.value == "POST" && call.request.uri == "/api/v2/transactions") {
                                call.respondText("\n${call.receiveText()}\n", ContentType.parse("application/x-ndjson"))
                            } else {
                                call.respond(HttpStatusCode.NotFound)
                            }
                        }
                    }
                }
            }.start()
        try {
            val port =
                server.engine
                    .resolvedConnectors()
                    .single()
                    .port
            val baseUrl = "http://127.0.0.1:$port/api"
            onBaseUrl(baseUrl)
            withTimeout(15.seconds) { block(AttoNodeClient.remote(baseUrl, AttoNodeStreamTransport.WEBSOCKET)) }
        } finally {
            server.stop(100, 1_000)
        }
    }

    private suspend fun DefaultWebSocketServerSession.sendResponse(response: AttoStreamResponse) {
        send(
            Frame.Text(
                AttoStreamProtocol.encodeResponse(
                    if (response.type ==
                        AttoStreamResponseType.DATA
                    ) {
                        response.copy(sequence = "1")
                    } else {
                        response
                    },
                ),
            ),
        )
    }
}
