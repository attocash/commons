package cash.atto.commons.node

import cash.atto.commons.AttoNetwork
import cash.atto.commons.fromHexToByteArray
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.AttoWorkerOperations
import cash.atto.commons.worker.cpu
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.net.ServerSocket

fun randomPort(): Int {
    ServerSocket(0).use { socket ->
        return socket.localPort
    }
}

class AttoMockWorker(
    val port: Int = randomPort(),
    network: AttoNetwork = AttoNetwork.LOCAL,
    worker: AttoWorker = AttoWorker.cpu(),
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}

    val server =
        embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                json()
            }

            routing {
                post("/works") {
                    val request = call.request.call.receive<AttoWorkerOperations.Request>()
                    val work = worker.work(network, request.timestamp, request.target.fromHexToByteArray())
                    call.respond(AttoWorkerOperations.Response(work))
                }
            }
        }

    fun start() {
        server.start()
    }

    fun stop() {
        server.stop()
    }

    override fun close() {
        stop()
    }
}
