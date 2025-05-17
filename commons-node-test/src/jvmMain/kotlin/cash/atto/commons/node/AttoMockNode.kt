package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoWork
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.ServerSocket

fun randomPort(): Int {
    ServerSocket(0).use { socket ->
        return socket.localPort
    }
}

class AttoMockNode(
    val port: Int = randomPort(),
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}

    val accountMap = mutableMapOf<AttoPublicKey, AttoAccount>()
    val transactionFlow = MutableSharedFlow<AttoTransaction>(replay = 10)
    val accountEntryFlow = MutableSharedFlow<AttoAccountEntry>(replay = 10)
    val receivableFlow = MutableSharedFlow<AttoReceivable>(replay = 10)

    val server =
        embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                json()
            }

            routing {

                get("/accounts/{publicKey}") {
                    val publicKeyString = call.parameters["publicKey"]

                    if (publicKeyString != null) {
                        val publicKey = AttoPublicKey.parse(publicKeyString)

                        val account = accountMap[publicKey]

                        if (account != null) {
                            call.respond(account)
                        } else {
                            call.respond(HttpStatusCode.NotFound, "Account not found")
                        }
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing publicKey")
                    }
                }

                post("/accounts") {
                    val search = call.request.call.receive<AttoNodeOperations.AccountSearch>()

                    val accounts = search.addresses.map { AttoAddress.parsePath(it) }.mapNotNull { accountMap[it.publicKey] }

                    call.respond(accounts)
                }

                get("/accounts/{publicKey}/transactions/stream") {
                    val publicKey = AttoPublicKey.parse(call.parameters["publicKey"]!!)

                    try {
                        call.respondBytesWriter(contentType = ContentType.parse("application/x-ndjson")) {
                            transactionFlow
                                .onStart { logger.info { "TransactionFlow(publicKey=$publicKey) started" } }
                                .onCompletion { logger.info { "TransactionFlow(publicKey=$publicKey) stopped" } }
                                .filter { it.block.publicKey == publicKey }
                                .collect {
                                    val ndjsonLine = Json.encodeToString(AttoTransaction.serializer(), it) + "\n"
                                    writeStringUtf8(ndjsonLine)
                                    flush()
                                    logger.info { "Emitted $it" }
                                }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "TransactionFlow(publicKey=$publicKey) failed" }
                    }
                }

                get("/accounts/{publicKey}/entries/stream") {
                    val publicKey = AttoPublicKey.parse(call.parameters["publicKey"]!!)

                    try {
                        call.respondBytesWriter(contentType = ContentType.parse("application/x-ndjson")) {
                            accountEntryFlow
                                .onStart { logger.info { "AccountEntryFlow(publicKey=$publicKey) started" } }
                                .onCompletion { logger.info { "AccountEntryFlow(publicKey=$publicKey) stopped" } }
                                .filter { it.publicKey == publicKey }
                                .collect {
                                    val ndjsonLine = Json.encodeToString(AttoAccountEntry.serializer(), it) + "\n"
                                    writeStringUtf8(ndjsonLine)
                                    flush()
                                    logger.info { "Emitted $it" }
                                }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "AccountEntryFlow(publicKey=$publicKey) failed" }
                    }
                }


                post("/accounts/entries/stream") {
                    val heightSearch = call.request.call.receive<AttoNodeOperations.HeightSearch>()
                    try {
                        call.respondBytesWriter(contentType = ContentType.parse("application/x-ndjson")) {
                            heightSearch.search
                                .asFlow()
                                .map { search -> accountEntryFlow.first { accountEntry -> accountEntry.height.value >= search.fromHeight && accountEntry.height.value <= search.toHeight } }
                                .onStart { logger.info { "Started /accounts/entries/stream $heightSearch" } }
                                .onCompletion { logger.info { "Completed /accounts/entries/stream $heightSearch" } }
                                .collect {
                                    val ndjsonLine = Json.encodeToString(AttoAccountEntry.serializer(), it) + "\n"
                                    writeStringUtf8(ndjsonLine)
                                    flush()
                                    logger.info { "Emitted $it" }
                                }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Error /accounts/entries/stream $heightSearch" }
                    }
                }

                get("/accounts/{publicKey}/receivables/stream") {
                    val publicKey = AttoPublicKey.parse(call.parameters["publicKey"]!!)
                    try {
                        call.respondBytesWriter(contentType = ContentType.parse("application/x-ndjson")) {
                            receivableFlow
                                .onStart { logger.info { "ReceivableFlow(publicKey=$publicKey) started" } }
                                .onCompletion { logger.info { "ReceivableFlow(publicKey=$publicKey) stopped" } }
                                .filter { it.receiverPublicKey == publicKey }
                                .collect {
                                    val ndjsonLine = Json.encodeToString(AttoReceivable.serializer(), it) + "\n"
                                    writeStringUtf8(ndjsonLine)
                                    flush()
                                    logger.info { "Emitted $it" }
                                }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "ReceivableFlow(publicKey=$publicKey) failed" }
                    }
                }

                post("/accounts/receivables/stream") {
                    val search = call.request.call.receive<AttoNodeOperations.AccountSearch>()
                    val publicKeys =
                        search
                            .addresses
                            .asSequence()
                            .map { AttoAddress.parsePath(it).publicKey }
                            .toSet()
                    try {
                        call.respondBytesWriter(contentType = ContentType.parse("application/x-ndjson")) {
                            receivableFlow
                                .onStart { logger.info { "ReceivableFlow(publicKeys=$publicKeys) started" } }
                                .onCompletion { logger.info { "ReceivableFlow(publicKeys=$publicKeys) stopped" } }
                                .filter { it.receiverPublicKey in publicKeys }
                                .collect {
                                    val ndjsonLine = Json.encodeToString(AttoReceivable.serializer(), it) + "\n"
                                    writeStringUtf8(ndjsonLine)
                                    flush()
                                    logger.info { "Emitted $it" }
                                }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "ReceivableFlow(publicKeys=$publicKeys) failed" }
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
