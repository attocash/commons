package cash.atto.commons.wallet

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoWork
import cash.atto.commons.gatekeeper.AttoJWT
import cash.atto.commons.toHex
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import kotlin.time.Duration.Companion.days


private val jwtAlgorithm by lazy {
    val keyPairGenerator = KeyPairGenerator.getInstance("EC")
    keyPairGenerator.initialize(521)
    val keyPair = keyPairGenerator.generateKeyPair()
    val privateKey = keyPair.private as ECPrivateKey
    Algorithm.ECDSA512(null, privateKey)
}

fun generateJwt(): AttoJWT {
    val jwt = JWT
        .create()
        .withIssuer("test")
        .withAudience("http://localhost")
        .withIssuedAt(java.time.Instant.now())
        .withExpiresAt(java.time.Instant.now().plusSeconds(1.days.inWholeSeconds))
        .withSubject(ByteArray(32).toHex())
        .sign(jwtAlgorithm)

    val decodedJWT = JWT.decode(jwt)

    return AttoJWT(decodedJWT.expiresAtAsInstant.toKotlinInstant(), jwt)
}

class AttoTestBackend(port: Int) {
    private val logger = KotlinLogging.logger {}

    val accountMap = mutableMapOf<AttoPublicKey, AttoAccount>()
    val transactionFlow = MutableSharedFlow<AttoTransaction>(replay = 10)
    val accountEntryFlow = MutableSharedFlow<AttoAccountEntry>(replay = 10)
    val receivableFlow = MutableSharedFlow<AttoReceivable>(replay = 10)

    val server = embeddedServer(CIO, port = port) {
        install(ContentNegotiation) {
            json()
        }

        routing {
            post("/login") {
                val response = TokenInitResponse(ByteArray(64).toHex())
                call.respond(response)
            }

            post("/login/challenges/{challenge}") {
                call.respond(generateJwt().encoded)
            }

            get("/accounts/{publicKey}") {
                val publicKeyString = call.parameters["publicKey"]

                if (publicKeyString != null) {
                    val publicKey = AttoPublicKey.parse(publicKeyString)

                    val account = accountMap[publicKey]

                    if (account != null) {
                        call.respond(account)  // Respond with the account if found
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Account not found")  // Respond with 404 if not found
                    }
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing publicKey")
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
                            .onEach { logger.info { "AccountEntryFlow(publicKey=$publicKey) stopped" } }
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
        server.start()
    }

    fun stop() {
        server.stop()
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
