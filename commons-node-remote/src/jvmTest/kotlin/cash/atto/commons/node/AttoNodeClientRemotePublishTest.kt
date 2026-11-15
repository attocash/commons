package cash.atto.commons.node

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoSendBlock
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoWork
import cash.atto.commons.toAttoVersion
import cash.atto.commons.transport.httpStatusCodeOrNull
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class AttoNodeClientRemotePublishTest {
    private val address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32) { 1 }))
    private val transaction =
        AttoTransaction(
            AttoSendBlock(
                AttoNetwork.LOCAL,
                0U.toAttoVersion(),
                address,
                AttoHeight(2UL),
                AttoAmount(7UL),
                AttoInstant.fromEpochMilliseconds(1_700_000_000_000),
                AttoHash(ByteArray(32) { 2 }),
                address,
                AttoAmount(1UL),
            ),
            AttoSignature(ByteArray(64)),
            AttoWork(ByteArray(8)),
        )

    @Test
    fun `publication uses HTTP with headers and matching NDJSON confirmation in both modes`(): Unit =
        runBlocking {
            // Given
            for (mode in AttoNodeStreamTransport.entries) {
                val requests = CopyOnWriteArrayList<String>()
                val authorization = CompletableDeferred<String>()
                val acceptedTypes = CompletableDeferred<String>()
                val body = CompletableDeferred<String>()
                withServer({ exchange ->
                    requests += "${exchange.requestMethod} ${exchange.requestURI}"
                    authorization.complete(exchange.requestHeaders.getFirst("Authorization"))
                    acceptedTypes.complete(exchange.requestHeaders.getFirst("Accept"))
                    body.complete(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
                    respond(exchange, 200, "\n \t\n${transaction.toJson()}\n")
                }) { baseUrl ->
                    val client = AttoNodeClient.remote(baseUrl, mode) { mapOf("Authorization" to "Bearer test") }
                    // When
                    client.publish(transaction)
                    // Then
                    assertEquals("Bearer test", authorization.await())
                    assertTrue("application/x-ndjson" in acceptedTypes.await().split(','))
                    assertEquals(transaction, AttoTransaction.fromJson(body.await()))
                    assertEquals(listOf("POST /prefix/v2/transactions"), requests.toList())
                }
            }
        }

    @Test
    fun `publication rejects HTTP errors and mismatched hashes without another request in both modes`(): Unit =
        runBlocking {
            // Given
            val other = transaction.copy(block = (transaction.block as AttoSendBlock).copy(height = AttoHeight(3UL)))
            for (mode in AttoNodeStreamTransport.entries) {
                for (status in listOf(400, 404, 429, 500, 200)) {
                    val requests = CopyOnWriteArrayList<String>()
                    withServer({ exchange ->
                        requests += "${exchange.requestMethod} ${exchange.requestURI}"
                        respond(exchange, status, other.toJson() + "\n")
                    }) { baseUrl ->
                        val client = AttoNodeClient.remote(baseUrl, mode)
                        // When
                        val failure = assertFailsWith<Exception> { client.publish(transaction) }
                        // Then
                        if (status == 200) {
                            assertTrue(failure is AttoNodePublishMismatchException)
                        } else {
                            assertEquals(status, failure.httpStatusCodeOrNull())
                        }
                        assertEquals(listOf("POST /prefix/v2/transactions"), requests.toList())
                    }
                }
            }
        }

    @Test
    fun `cancelling publication closes its HTTP response in WebSocket mode`(): Unit =
        runBlocking {
            // Given
            val started = CompletableDeferred<Unit>()
            val closed = CompletableDeferred<Unit>()
            withServer({ exchange ->
                exchange.requestBody.readBytes()
                exchange.responseHeaders.add("Content-Type", "application/x-ndjson")
                exchange.sendResponseHeaders(200, 0)
                started.complete(Unit)
                try {
                    while (true) {
                        exchange.responseBody.write('\n'.code)
                        exchange.responseBody.flush()
                        Thread.sleep(20)
                    }
                } catch (_: Exception) {
                    closed.complete(Unit)
                }
            }) { baseUrl ->
                val client = AttoNodeClient.remote(baseUrl, AttoNodeStreamTransport.WEBSOCKET)
                val publishing = launch { client.publish(transaction) }
                withTimeout(5.seconds) { started.await() }
                // When
                publishing.cancelAndJoin()
                // Then
                withTimeout(5.seconds) { closed.await() }
            }
        }

    @Test
    fun `HTTP publication deadline remains one minute despite heartbeats in WebSocket mode`(): Unit =
        runBlocking {
            // Given
            val requests = CopyOnWriteArrayList<String>()
            withServer({ exchange ->
                requests += "${exchange.requestMethod} ${exchange.requestURI}"
                exchange.requestBody.readBytes()
                exchange.responseHeaders.add("Content-Type", "application/x-ndjson")
                exchange.sendResponseHeaders(200, 0)
                try {
                    while (true) {
                        exchange.responseBody.write('\n'.code)
                        exchange.responseBody.flush()
                        Thread.sleep(100)
                    }
                } catch (_: Exception) {
                    // The deadline closes the response while heartbeats are still arriving.
                }
            }) { baseUrl ->
                val client = AttoNodeClient.remote(baseUrl, AttoNodeStreamTransport.WEBSOCKET)
                val started = TimeSource.Monotonic.markNow()
                // When
                val failure = withTimeout(70.seconds) { assertFailsWith<Exception> { client.publish(transaction) } }
                // Then
                assertTrue(started.elapsedNow() >= 59.seconds)
                assertTrue(started.elapsedNow() < 65.seconds)
                assertTrue(failure.stackTraceToString().contains("timeout", ignoreCase = true))
                assertEquals(listOf("POST /prefix/v2/transactions"), requests.toList())
            }
        }

    private suspend fun withServer(
        handler: (HttpExchange) -> Unit,
        block: suspend (String) -> Unit,
    ) {
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/") { exchange ->
            try {
                handler(exchange)
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/prefix")
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        body: String,
    ) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/x-ndjson")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }
}
