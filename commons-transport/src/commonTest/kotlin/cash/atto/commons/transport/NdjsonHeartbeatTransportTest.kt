package cash.atto.commons.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class NdjsonHeartbeatTransportTest {
    @Test
    fun `v2 GET and POST decode heartbeats through HTTP with explicit stream timeouts`() =
        runTest {
            // Given
            val seenMethods = mutableListOf<HttpMethod>()
            val engine =
                MockEngine { request ->
                    seenMethods.add(request.method)
                    assertEquals("/v2/accounts/stream", request.url.encodedPath)
                    assertEquals("application/x-ndjson", request.headers[HttpHeaders.Accept])
                    assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                    val timeouts = request.getCapabilityOrNull(HttpTimeoutCapability)
                    assertEquals(Long.MAX_VALUE, timeouts?.requestTimeoutMillis)
                    assertEquals(1.hours.inWholeMilliseconds, timeouts?.socketTimeoutMillis)
                    assertEquals(10.seconds.inWholeMilliseconds, timeouts?.connectTimeoutMillis)
                    if (request.method == HttpMethod.Post) {
                        assertEquals("{\"value\":\"search\"}", request.body.toByteArray().decodeToString())
                        assertEquals("application/json", request.body.contentType.toString())
                    }
                    respond(
                        "\n \t\n{\"value\":\"one\"}\n\n{\"value\":\"two\"}\n\t \n\n",
                        headers = headersOf(HttpHeaders.ContentType, "application/x-ndjson"),
                    )
                }
            val transport = transport(engine)
            val timeouts = AttoHttpTimeouts(request = Duration.INFINITE, socket = 1.hours, connect = 10.seconds)
            try {
                // When
                val getValues = transport.getNdjson<Record>("v2/accounts/stream", timeouts).toList()
                val postValues = transport.postNdjson<Record, Record>("v2/accounts/stream", Record("search"), timeouts).toList()

                // Then
                assertEquals(listOf(Record("one"), Record("two")), getValues)
                assertEquals(getValues, postValues)
                assertEquals(listOf(HttpMethod.Get, HttpMethod.Post), seenMethods)
            } finally {
                transport.client.close()
            }
        }

    @Test
    fun `heartbeat-only finite responses complete without emitting domain values`() =
        runTest {
            // Given
            val engine = MockEngine { respond("\n \t\n\n", headers = headersOf(HttpHeaders.ContentType, "application/x-ndjson")) }
            val transport = transport(engine)
            try {
                // When
                val getValues = transport.getNdjson<Record>("v2/accounts/stream").toList()
                val postValues = transport.postNdjson<Record, Record>("v2/accounts/stream", Record("search")).toList()

                // Then
                assertEquals(emptyList(), getValues)
                assertEquals(emptyList(), postValues)
            } finally {
                transport.client.close()
            }
        }

    @Test
    fun `heartbeat-only response keeps awaiting domain data until collection is cancelled`() =
        runTest {
            // Given
            val body = ByteChannel(autoFlush = true)
            val requested = CompletableDeferred<Unit>()
            val engine =
                MockEngine {
                    requested.complete(Unit)
                    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/x-ndjson"))
                }
            val transport = transport(engine)
            val values = mutableListOf<Record>()
            val collection = backgroundScope.launch { transport.getNdjson<Record>("v2/accounts/stream").toList(values) }
            requested.await()
            backgroundScope.launch {
                delay(1.seconds)
                body.writeStringUtf8("\n")
            }
            try {
                // When
                advanceTimeBy(1.seconds)
                runCurrent()

                // Then
                assertTrue(collection.isActive)
                assertEquals(emptyList(), values)

                // When
                collection.cancelAndJoin()

                // Then
                assertTrue(collection.isCancelled)
                assertEquals(emptyList(), values)
            } finally {
                body.cancel(null)
                transport.client.close()
            }
        }

    private fun transport(engine: MockEngine): AttoHttpTransport =
        AttoHttpTransport("https://node.example", headerProvider = { mapOf("Authorization" to "Bearer token") }).also {
            it.client =
                HttpClient(engine) {
                    install(ContentNegotiation) { json(transportJson) }
                    install(HttpTimeout)
                    expectSuccess = true
                }
        }

    @Serializable
    private data class Record(
        val value: String,
    )
}
