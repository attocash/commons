package cash.atto.commons.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class AttoHttpTransportTest {
    @Test
    fun `get applies headers and decodes response`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("https://node.example/items", request.url.toString())
                    assertEquals("Bearer token", request.headers["Authorization"])
                    respond(
                        content = """{"value":"ok","ignored":true}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val transport = transport(engine)

            val response = transport.get<Response>("items")

            assertEquals(Response("ok"), response)
        }

    @Test
    fun `get exposes HTTP status through transport helper`() =
        runTest {
            val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
            val transport = transport(engine)

            val status =
                runCatching { transport.get<Response>("missing") }
                    .exceptionOrNull()
                    ?.httpStatusCodeOrNull()

            assertEquals(HttpStatusCode.NotFound.value, status)
        }

    @Test
    fun `ndjson response is decoded as a flow`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = "{\"value\":\"one\"}\n{\"value\":\"two\"}\n",
                        headers = headersOf(HttpHeaders.ContentType, "application/x-ndjson"),
                    )
                }
            val transport = transport(engine)

            val values = transport.getNdjson<Response>("stream").toList()

            assertEquals(listOf(Response("one"), Response("two")), values)
        }

    private fun transport(engine: MockEngine): AttoHttpTransport =
        AttoHttpTransport(
            baseUrl = "https://node.example",
            headerProvider = { mapOf("Authorization" to "Bearer token") },
        ).also {
            it.client =
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(transportJson)
                    }
                    expectSuccess = true
                }
        }

    @Serializable
    private data class Response(
        val value: String,
    )
}
