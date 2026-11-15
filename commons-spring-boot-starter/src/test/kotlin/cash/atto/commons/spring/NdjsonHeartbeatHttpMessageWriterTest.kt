package cash.atto.commons.spring

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.core.ResolvableType
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.http.server.reactive.MockServerHttpResponse
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.RequestPredicates
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.netty.http.server.HttpServer
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher
import reactor.test.scheduler.VirtualTimeScheduler
import java.io.IOException
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Timeout(10)
class NdjsonHeartbeatHttpMessageWriterTest {
    private val httpClient = HttpClient.newHttpClient()
    private val interval = Duration.ofMillis(80)
    private val elementType = ResolvableType.forClass(Record::class.java)
    private val json =
        Json {
            encodeDefaults = false
            explicitNulls = false
            serializersModule = SerializersModule { contextual(DecimalSerializer) }
        }

    @AfterEach
    fun closeHttpClient() {
        httpClient.close()
    }

    @Test
    fun `real socket flushes initial and consecutive heartbeats before custom serialized record`() {
        // Given
        val subscriptions = AtomicInteger()
        val cancellations = AtomicInteger()
        val firstRecord =
            reactor.core.publisher.Sinks
                .one<Record>()
        val source =
            Flux
                .concat(firstRecord.asMono(), Flux.never())
                .doOnSubscribe { subscriptions.incrementAndGet() }
                .doOnCancel { cancellations.incrementAndGet() }

        withServer(source) { url ->
            // When
            val response = request(url)
            response.body().bufferedReader().use { reader ->
                assertEquals(200, response.statusCode())
                assertEquals("", reader.readLine())
                assertEquals("", reader.readLine())
                assertEquals(reactor.core.publisher.Sinks.EmitResult.OK, firstRecord.tryEmitValue(Record(BigDecimal("1.250"))))
                val record = generateSequence { reader.readLine() }.first { it.isNotBlank() }

                // Then
                assertEquals("{\"amount\":\"custom:1.250\"}", record)
                assertEquals(1, subscriptions.get())
            }
            await { cancellations.get() == 1 }
        }
    }

    @Test
    fun `real socket heartbeats delayed Mono and completes after its record`() {
        // Given
        val source = Mono.delay(interval.multipliedBy(3)).map { Record(BigDecimal.ONE) }

        withServer(source) { url ->
            // When
            val response = request(url)
            val lines = response.body().bufferedReader().use { it.readLines() }

            // Then
            assertTrue(lines.first().isBlank())
            assertEquals(listOf("{\"amount\":\"custom:1\"}"), lines.filter { it.isNotBlank() })
        }
    }

    @Test
    fun `transaction publication POST flushes heartbeats before delayed records`() {
        // Given
        val source = Mono.delay(interval.multipliedBy(3)).map { Record(BigDecimal.ONE) }
        withServer(source, "/v2/transactions", HttpMethod.POST) { url ->
            // When
            val response = request(url, HttpMethod.POST)
            val lines = response.body().bufferedReader().use { it.readLines() }

            // Then
            assertEquals(200, response.statusCode())
            assertTrue(lines.first().isBlank())
            assertEquals(listOf("{\"amount\":\"custom:1\"}"), lines.filter { it.isNotBlank() })
        }
    }

    @Test
    fun `empty stream completes without heartbeats or records`() {
        // Given
        withServer(Mono.empty()) { url ->
            // When
            val response = request(url)
            val body = response.body().use { it.readAllBytes() }

            // Then
            assertEquals(200, response.statusCode())
            assertEquals(0, body.size)
        }
    }

    @Test
    fun `finite stream flushes complete records between heartbeats`() {
        // Given
        val source = Flux.just(Record(BigDecimal.ONE), Record(BigDecimal.TEN)).delayElements(interval.multipliedBy(3))
        withServer(source) { url ->
            // When
            val response = request(url)
            val lines = response.body().bufferedReader().use { it.readLines() }

            // Then
            assertTrue(lines.first().isBlank())
            assertEquals(
                listOf(
                    "{\"amount\":\"custom:1\"}",
                    "{\"amount\":\"custom:10\"}",
                ),
                lines.filter { it.isNotBlank() },
            )
            val firstRecord = lines.indexOfFirst { it.isNotBlank() }
            assertTrue(lines.drop(firstRecord + 1).any { it.isBlank() })
        }
    }

    @Test
    fun `error after flushed heartbeat aborts socket without a confirmation record`() {
        // Given
        val source = Mono.delay(interval.multipliedBy(3)).then(Mono.error<Record>(IllegalStateException("source failure")))
        withServer(source) { url ->
            val response = request(url)
            response.body().bufferedReader().use { reader ->
                // When
                assertEquals("", reader.readLine())
                val records = mutableListOf<String>()
                assertThrows(IOException::class.java) {
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isNotBlank()) records += line
                    }
                }

                // Then
                assertEquals(200, response.statusCode())
                assertTrue(records.isEmpty())
            }
        }
    }

    @Test
    fun `slow consumer retains at most one tick and preserves subsequent record`() {
        // Given
        val scheduler = VirtualTimeScheduler.create()
        VirtualTimeScheduler.set(scheduler)
        val source = TestPublisher.create<Record>()
        val cancellations = AtomicInteger()
        val encoder = NdjsonHeartbeatEncoder(json)
        val hints =
            encoder.getEncodeHints(
                elementType,
                elementType,
                MediaType.APPLICATION_NDJSON,
                MockServerHttpRequest.get("/v2/items/stream").build(),
                MockServerHttpResponse(),
            )
        val body =
            encoder
                .encode(
                    source.flux().doOnCancel { cancellations.incrementAndGet() },
                    DefaultDataBufferFactory.sharedInstance,
                    elementType,
                    MediaType.APPLICATION_NDJSON,
                    hints,
                ).map { buffer -> buffer.toString(Charsets.UTF_8).also { DataBufferUtils.release(buffer) } }
        val received = AtomicInteger()

        try {
            // When
            StepVerifier
                .create(body.doOnNext { received.incrementAndGet() }, 0)
                .thenRequest(1)
                .then { scheduler.advanceTimeBy(Duration.ofMillis(999)) }
                .then { assertEquals(0, received.get()) }
                .then { scheduler.advanceTimeBy(Duration.ofMillis(1)) }
                .expectNext("\n")
                .then { scheduler.advanceTimeBy(Duration.ofDays(2)) }
                .thenRequest(1)
                .expectNext("\n")
                .thenRequest(1)
                .then { scheduler.advanceTimeBy(Duration.ofMillis(999)) }
                .then { assertEquals(2, received.get()) }
                .then { source.next(Record(BigDecimal.ONE)) }
                .expectNext("{\"amount\":\"custom:1\"}\n")
                .thenCancel()
                .verify(Duration.ofSeconds(3))

            // Then
            assertEquals(1, cancellations.get())
            val scheduledTasks = scheduler.scheduledTaskCount
            scheduler.advanceTimeBy(Duration.ofDays(1))
            assertEquals(scheduledTasks, scheduler.scheduledTaskCount)
        } finally {
            scheduler.dispose()
            VirtualTimeScheduler.reset()
        }
    }

    @Test
    fun `precommit error preserves HTTP status`() {
        // Given
        val source = Mono.delay(Duration.ofMillis(10)).then(Mono.error<Record>(ResponseStatusException(HttpStatus.NOT_FOUND)))

        withServer(source) { url ->
            // When
            val response = request(url)

            // Then
            response.body().use { assertEquals(404, response.statusCode()) }
        }
    }

    @Test
    fun `default writer heartbeats every second and stops timer and upstream on termination`() {
        val requests =
            listOf(
                MockServerHttpRequest.get("/v2/items/stream").build(),
                MockServerHttpRequest.post("/v2/transactions").build(),
            )
        for (request in requests) {
            for (termination in listOf("complete", "error", "cancel")) {
                // Given
                val scheduler = VirtualTimeScheduler.create()
                val subscriptions = AtomicInteger()
                val cancellations = AtomicInteger()
                val source = TestPublisher.create<Record>()
                val writer = NdjsonHeartbeatHttpMessageWriter(json)
                val heartbeats = mutableListOf<String>()
                val response = MockServerHttpResponse()
                response.setWriteHandler { buffers ->
                    buffers
                        .doOnNext { buffer ->
                            heartbeats += buffer.toString(Charsets.UTF_8)
                            DataBufferUtils.release(buffer)
                        }.then()
                }
                val body = source.flux().doOnSubscribe { subscriptions.incrementAndGet() }.doOnCancel { cancellations.incrementAndGet() }

                // When
                VirtualTimeScheduler.set(scheduler)
                StepVerifier
                    .create(
                        writer.write(
                            body,
                            elementType,
                            elementType,
                            MediaType.APPLICATION_NDJSON,
                            request,
                            response,
                            emptyMap(),
                        ),
                    ).then { scheduler.advanceTimeBy(Duration.ofMillis(999)) }
                    .then { assertTrue(heartbeats.isEmpty()) }
                    .then { scheduler.advanceTimeBy(Duration.ofMillis(1)) }
                    .then { assertEquals(listOf("\n"), heartbeats) }
                    .then { scheduler.advanceTimeBy(Duration.ofMillis(999)) }
                    .then { assertEquals(listOf("\n"), heartbeats) }
                    .then { scheduler.advanceTimeBy(Duration.ofMillis(1)) }
                    .then { assertEquals(listOf("\n", "\n"), heartbeats) }
                    .then {
                        assertEquals(1, subscriptions.get())
                        if (termination == "complete") source.complete()
                        if (termination == "error") source.error(IllegalStateException("source failure"))
                    }.let { verifier ->
                        when (termination) {
                            "complete" -> verifier.verifyComplete()
                            "error" -> verifier.verifyErrorMessage("source failure")
                            else -> verifier.thenCancel().verify()
                        }
                    }

                // Then
                val scheduledTasks = scheduler.scheduledTaskCount
                scheduler.advanceTimeBy(Duration.ofDays(1))
                assertEquals(scheduledTasks, scheduler.scheduledTaskCount)
                if (termination != "complete") assertEquals(1, cancellations.get())
                scheduler.dispose()
                VirtualTimeScheduler.reset()
            }
        }
    }

    @Test
    fun `legacy streams and v2 ordinary JSON keep their exact encoding`() {
        // Given
        val source = Mono.delay(interval.multipliedBy(3)).map { Record(BigDecimal.ONE) }
        val baseline = KotlinSerializationJsonEncoder(json)
        for ((path, mediaType, method) in listOf(
            Triple("/items/stream", MediaType.APPLICATION_NDJSON, HttpMethod.GET),
            Triple("/v2/items", MediaType.APPLICATION_NDJSON, HttpMethod.GET),
            Triple("/v2/transactions/pending", MediaType.APPLICATION_NDJSON, HttpMethod.GET),
            Triple("/v2/transactions/", MediaType.APPLICATION_NDJSON, HttpMethod.GET),
            Triple("/transactions", MediaType.APPLICATION_NDJSON, HttpMethod.GET),
            Triple("/v2/transactions", MediaType.APPLICATION_JSON, HttpMethod.POST),
            Triple("/v2/transactions", MediaType.APPLICATION_NDJSON, HttpMethod.GET),
            Triple("/v2/items/stream", MediaType.APPLICATION_JSON, HttpMethod.GET),
        )) {
            val writer = NdjsonHeartbeatHttpMessageWriter(json, interval)
            val response = MockServerHttpResponse()
            val expected =
                baseline
                    .encode(source, DefaultDataBufferFactory.sharedInstance, elementType, mediaType, emptyMap())
                    .map { buffer ->
                        buffer.toString(Charsets.UTF_8).also { DataBufferUtils.release(buffer) }
                    }.collectList()
                    .block()!!
                    .joinToString("")

            // When
            writer
                .write(
                    source,
                    elementType,
                    elementType,
                    mediaType,
                    MockServerHttpRequest.method(method, path).build(),
                    response,
                    emptyMap(),
                ).block()

            // Then
            assertEquals(expected, response.bodyAsString.block())
        }
    }

    @Test
    fun `starter keeps default codecs until application opts into heartbeats`() {
        // Given
        val server = ServerCodecConfigurer.create()
        val client =
            org.springframework.http.codec.ClientCodecConfigurer
                .create()
        val customizer = AttoAutoConfiguration().kotlinxCodecCustomizer()

        // When
        customizer.customize(server)
        customizer.customize(client)

        // Then
        assertTrue(server.writers.none { it is NdjsonHeartbeatHttpMessageWriter })
        assertTrue(client.writers.none { it is NdjsonHeartbeatHttpMessageWriter })
    }

    private fun withServer(
        source: org.reactivestreams.Publisher<Record>,
        path: String = "/v2/items/stream",
        method: HttpMethod = HttpMethod.GET,
        action: (String) -> Unit,
    ) {
        val route =
            RouterFunctions.route(RequestPredicates.path(path).and(RequestPredicates.method(method))) {
                ServerResponse.ok().contentType(MediaType.APPLICATION_NDJSON).body(source, Record::class.java)
            }
        val strategies =
            HandlerStrategies
                .builder()
                .codecs {
                    AttoAutoConfiguration().kotlinxCodecCustomizer().customize(it)
                    it.customCodecs().register(NdjsonHeartbeatHttpMessageWriter(json, interval))
                }.build()
        val server =
            HttpServer
                .create()
                .host("127.0.0.1")
                .port(0)
                .handle(ReactorHttpHandlerAdapter(RouterFunctions.toHttpHandler(route, strategies)))
                .bindNow()
        try {
            action("http://127.0.0.1:${server.port()}$path")
        } finally {
            server.disposeNow()
        }
    }

    private fun request(
        url: String,
        method: HttpMethod = HttpMethod.GET,
    ): HttpResponse<java.io.InputStream> =
        HttpClient
            .newHttpClient()
            .sendAsync(
                HttpRequest
                    .newBuilder(URI(url))
                    .timeout(Duration.ofSeconds(3))
                    .method(method.name(), HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofInputStream(),
            ).get(3, TimeUnit.SECONDS)

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(condition())
    }

    @Serializable
    private data class Record(
        @Contextual val amount: BigDecimal,
        val enabled: Boolean = true,
        val missing: String? = null,
    )

    private object DecimalSerializer : KSerializer<BigDecimal> {
        override val descriptor = PrimitiveSerialDescriptor("Decimal", PrimitiveKind.STRING)

        override fun serialize(
            encoder: Encoder,
            value: BigDecimal,
        ) = encoder.encodeString("custom:${value.toPlainString()}")

        override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString().removePrefix("custom:"))
    }
}
