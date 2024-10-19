package cash.atto.commons.wallet

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoWork
import cash.atto.commons.gatekeeper.AttoJWT
import cash.atto.commons.toHex
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

expect fun generateJwt(): AttoJWT

class AttoTestBackend(port: Int) {
    private val logger = KotlinLogging.logger {}

    val accountFlow = MutableSharedFlow<AttoAccount>(replay = 10)
    val transactionFlow = MutableSharedFlow<AttoTransaction>(replay = 10)
    val receivableFlow = MutableSharedFlow<AttoReceivable>(replay = 10)

    private val scope = CoroutineScope(Dispatchers.Default)

    val server = embeddedServer(CIO, port = port) {
        install(ContentNegotiation) {
            json()
        }

        routing {
            post("/login") {
                val response = TokenInitResponse(ByteArray(60).toHex())
                call.respond(response)
            }

            post("/login/challenges/{challenge}") {
                call.respond(generateJwt().encoded)
            }

            get("/accounts/{publicKey}/stream") {
                val publicKey = AttoPublicKey.parse(call.parameters["publicKey"]!!)

                try {
                    call.respondBytesWriter(contentType = ContentType.parse("application/x-ndjson")) {
                        accountFlow
                            .onStart { logger.info { "AccountFlow(publicKey=$publicKey) started" } }
                            .onEach { logger.info { "AccountFlow(publicKey=$publicKey) stopped" } }
                            .filter { it.publicKey == publicKey }
                            .collect {
                                val ndjsonLine = Json.encodeToString(AttoAccount.serializer(), it) + "\n"
                                writeStringUtf8(ndjsonLine)
                                flush()
                                logger.info { "Emitted $it" }
                            }
                    }
                } catch (e: CancellationException) {
                    logger.debug(e) { "AccountFlow(publicKey=$publicKey) cancelled" }
                } catch (e: Exception) {
                    logger.error(e) { "AccountFlow(publicKey=$publicKey) failed" }
                }
            }

            get("/accounts/{publicKey}/transactions/stream") {
                val publicKey = AttoPublicKey.parse(call.parameters["publicKey"]!!)

                try {
                    call.respondBytesWriter(contentType = ContentType.parse("application/x-ndjson")) {
                        transactionFlow
                            .onStart { logger.info { "TransactionFlow(publicKey=$publicKey) started" } }
                            .onEach { logger.info { "TransactionFlow(publicKey=$publicKey) stopped" } }
                            .filter { it.block.publicKey == publicKey }
                            .collect {
                                val ndjsonLine = Json.encodeToString(AttoTransaction.serializer(), it) + "\n"
                                writeStringUtf8(ndjsonLine)
                                flush()
                                logger.info { "Emitted $it" }
                            }
                    }
                } catch (e: CancellationException) {
                    logger.debug(e) { "TransactionFlow(publicKey=$publicKey) cancelled" }
                } catch (e: Exception) {
                    logger.error(e) { "TransactionFlow(publicKey=$publicKey) failed" }
                }
            }

            get("/accounts/{publicKey}/receivables/stream") {
                val publicKey = AttoPublicKey.parse(call.parameters["publicKey"]!!)
                try {
                    call.respondBytesWriter(contentType = ContentType.parse("application/x-ndjson")) {
                        receivableFlow
                            .onStart { logger.info { "ReceivableFlow(publicKey=$publicKey) started" } }
                            .onEach { logger.info { "ReceivableFlow(publicKey=$publicKey) stopped" } }
                            .filter { it.receiverPublicKey == publicKey }
                            .collect {
                                val ndjsonLine = Json.encodeToString(AttoReceivable.serializer(), it) + "\n"
                                writeStringUtf8(ndjsonLine)
                                flush()
                                logger.info { "Emitted $it" }

                            }
                    }
                } catch (e: CancellationException) {
                    logger.debug(e) { "ReceivableFlow(publicKey=$publicKey) cancelled" }
                } catch (e: Exception) {
                    logger.error(e) { "ReceivableFlow(publicKey=$publicKey) failed" }
                }
            }

            post("/transactions/stream") {
                call.response.headers.append("Content-Type", "application/x-ndjson")

                val transaction = call.request.call.receive<AttoTransaction>()

                transactionFlow.emit(transaction)

                val ndjsonLine = Json.encodeToString(AttoTransaction.serializer(), transaction) + "\n"
                call.respondText(ndjsonLine, ContentType.parse("application/x-ndjson"))

            }

            get("/instants/{instant}") {
                val clientInstant = Instant.parse(call.parameters["instant"]!!)
                val serverInstant = Clock.System.now()
                val differenceMillis = serverInstant.minus(clientInstant).inWholeMilliseconds

                val response = InstantResponse(clientInstant, serverInstant, differenceMillis)

                call.respond(response)
            }

            post("/works") {
                val response = WorkResponse(AttoWork(ByteArray(8)))
                call.respond(response)
            }
        }
    }

    fun start() {
        scope.launch { server.start(wait = true) }
    }

    fun stop() {
        server.stop()
        scope.cancel()
    }

    @Serializable
    data class InstantResponse(
        val clientInstant: Instant,
        val serverInstant: Instant,
        val differenceMillis: Long,
    )

    @Serializable
    data class TokenInitRequest(
        val algorithm: AttoAlgorithm,
        val publicKey: AttoPublicKey,
    )

    @Serializable
    data class TokenInitResponse(
        val challenge: String,
    )

    @Serializable
    data class TokenInitAnswer(
        val signature: AttoSignature,
    )

    @Serializable
    private data class WorkResponse(
        val work: AttoWork,
    )
}
