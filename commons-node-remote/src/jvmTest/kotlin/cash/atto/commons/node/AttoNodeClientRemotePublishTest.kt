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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

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
    fun `publication preserves the default route and opts into deduplication including Java overloads`(): Unit =
        runBlocking {
            // Given
            val requests = CopyOnWriteArrayList<String>()
            val bodies = CopyOnWriteArrayList<String>()
            val headers = CopyOnWriteArrayList<String>()
            withServer({ exchange ->
                requests += "${exchange.requestMethod} ${exchange.requestURI}"
                bodies += exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                headers += exchange.requestHeaders.getFirst("Authorization")
                respond(exchange, 200, transaction.toJson() + "\n")
            }) { baseUrl ->
                val client = AttoNodeClient.remote(baseUrl) { mapOf("Authorization" to "Bearer test") }
                val async = AttoNodeClientAsyncBuilder(baseUrl).header("Authorization", "Bearer test").build()
                try {
                    val javaPublish = async.javaClass.getMethod("publish", AttoTransaction::class.java)
                    val javaDeduplicatedPublish =
                        async.javaClass.getMethod("publish", AttoTransaction::class.java, java.lang.Boolean.TYPE)
                    // When
                    client.publish(transaction)
                    client.publish(transaction, false)
                    client.publish(transaction, true)
                    (javaPublish.invoke(async, transaction) as CompletableFuture<*>).get(5, TimeUnit.SECONDS)
                    (javaDeduplicatedPublish.invoke(async, transaction, true) as CompletableFuture<*>).get(5, TimeUnit.SECONDS)
                    // Then
                    assertEquals(
                        listOf(
                            "POST /prefix/transactions/stream",
                            "POST /prefix/transactions/stream",
                            "POST /prefix/transactions/stream?deduplicate=true",
                            "POST /prefix/transactions/stream",
                            "POST /prefix/transactions/stream?deduplicate=true",
                        ),
                        requests.toList(),
                    )
                    assertEquals(List(5) { transaction }, bodies.map { AttoTransaction.fromJson(it) })
                    assertEquals(List(5) { "Bearer test" }, headers.toList())
                } finally {
                    async.close()
                }
            }
        }

    @Test
    fun `deduplicated publication rejects HTTP errors and another transaction acknowledgement without retrying`(): Unit =
        runBlocking {
            // Given
            val other = transaction.copy(block = (transaction.block as AttoSendBlock).copy(height = AttoHeight(3UL)))
            for (status in listOf(400, 404, 429, 503, 200)) {
                val requests = CopyOnWriteArrayList<String>()
                withServer({ exchange ->
                    requests += "${exchange.requestMethod} ${exchange.requestURI}"
                    respond(exchange, status, other.toJson() + "\n")
                }) { baseUrl ->
                    val client = AttoNodeClient.remote(baseUrl)
                    // When
                    val failure = assertFailsWith<Exception> { client.publish(transaction, true) }
                    // Then
                    if (status == 200) {
                        assertTrue(failure is AttoNodePublishMismatchException)
                    } else {
                        assertEquals(status, failure.httpStatusCodeOrNull())
                    }
                    assertEquals(listOf("POST /prefix/transactions/stream?deduplicate=true"), requests.toList())
                }
            }
        }

    @Test
    fun `cancelling deduplicated publication closes the pending HTTP response`(): Unit =
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
                        // Leave the first record incomplete until publication is cancelled.
                        exchange.responseBody.write(' '.code)
                        exchange.responseBody.flush()
                        Thread.sleep(20)
                    }
                } catch (_: java.io.IOException) {
                    closed.complete(Unit)
                }
            }) { baseUrl ->
                val client = AttoNodeClient.remote(baseUrl)
                val publishing = launch { client.publish(transaction, true) }
                withTimeout(5.seconds) { started.await() }
                // When
                publishing.cancelAndJoin()
                // Then
                withTimeout(5.seconds) { closed.await() }
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
