package cash.atto.commons.worker

import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AttoWorkerAsyncBuilderTest {
    @Test
    fun `built workers read current builder headers for each request`(): Unit =
        runBlocking {
            // Given
            val target = AttoWorkTarget(ByteArray(32))
            val work = localWork(target)
            val headers = CopyOnWriteArrayList<Pair<String?, String?>>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/works") { exchange ->
                headers += exchange.requestHeaders.getFirst("Authorization") to exchange.requestHeaders.getFirst("X-Custom")
                exchange.respond(work)
            }
            server.start()
            val builder =
                AttoWorkerAsyncBuilder("http://127.0.0.1:${server.address.port}")
                    .cached(false)
                    .headers(mapOf("Authorization" to "first"))
                    .header("X-Custom", "present")
            val worker = builder.build().worker
            try {
                // When
                val first = worker.work(AttoNetwork.LOCAL, AttoNetwork.INITIAL_INSTANT, target)
                builder.headers(mapOf("Authorization" to "second"))
                val second = worker.work(AttoNetwork.LOCAL, AttoNetwork.INITIAL_INSTANT, target)

                // Then
                assertEquals(work, first)
                assertEquals(work, second)
                assertEquals(listOf("first" to "present", "second" to null), headers.toList())
            } finally {
                worker.close()
                server.stop(0)
            }
        }

    @Test
    fun `default cached worker retries failed remote calculations on JVM`(): Unit =
        runBlocking {
            // Given
            val target = AttoWorkTarget(ByteArray(32))
            val work = localWork(target)
            val attempts = AtomicInteger()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/works") { exchange ->
                if (attempts.incrementAndGet() == 1) {
                    exchange.requestBody.close()
                    exchange.sendResponseHeaders(503, -1)
                    exchange.close()
                } else {
                    exchange.respond(work)
                }
            }
            server.start()
            val worker =
                AttoWorkerAsyncBuilder("http://127.0.0.1:${server.address.port}")
                    .retryEvery(1.milliseconds)
                    .build()
                    .worker
            try {
                // When
                val result =
                    withTimeout(10.seconds) {
                        worker.work(AttoNetwork.LOCAL, AttoNetwork.INITIAL_INSTANT, target)
                    }

                // Then
                assertEquals(work, result)
                assertEquals(2, attempts.get())
            } finally {
                worker.close()
                server.stop(0)
            }
        }

    private suspend fun localWork(target: AttoWorkTarget): AttoWork =
        AttoWorker.cpu(1U).use { worker ->
            worker.work(AttoNetwork.LOCAL, AttoNetwork.INITIAL_INSTANT, target)
        }

    private fun HttpExchange.respond(work: AttoWork) {
        requestBody.close()
        val response = """{"work":"$work"}""".encodeToByteArray()
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(200, response.size.toLong())
        responseBody.use { it.write(response) }
        close()
    }
}
