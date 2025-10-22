package cash.atto.commons.gatekeeper

import cash.atto.commons.AttoInstant
import cash.atto.commons.toAtto
import cash.atto.commons.toHex
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import java.net.ServerSocket
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import kotlin.time.Duration.Companion.days

private fun randomPort(): Int {
    ServerSocket(0).use { socket ->
        return socket.localPort
    }
}

private val jwtAlgorithm by lazy {
    val keyPairGenerator = KeyPairGenerator.getInstance("EC")
    keyPairGenerator.initialize(521)
    val keyPair = keyPairGenerator.generateKeyPair()
    val privateKey = keyPair.private as ECPrivateKey
    Algorithm.ECDSA512(null, privateKey)
}

fun generateJwt(): AttoJWT {
    val jwt =
        JWT
            .create()
            .withIssuer("test")
            .withAudience("http://localhost")
            .withIssuedAt(java.time.Instant.now())
            .withExpiresAt(
                java
                    .time
                    .Instant
                    .now()
                    .plusSeconds(1.days.inWholeSeconds),
            ).withSubject(ByteArray(32).toHex())
            .sign(jwtAlgorithm)

    val decodedJWT = JWT.decode(jwt)

    return AttoJWT(decodedJWT.expiresAtAsInstant.toAtto(), jwt)
}

class AttoMockGatekeeper(
    val port: Int = randomPort(),
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}

    val server =
        embeddedServer(CIO, port = port) {
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
    data class TokenInitResponse(
        val challenge: String,
    )

    @Serializable
    data class AttoWorkRequest(
        val timestamp: AttoInstant,
        val target: String,
    )
}
