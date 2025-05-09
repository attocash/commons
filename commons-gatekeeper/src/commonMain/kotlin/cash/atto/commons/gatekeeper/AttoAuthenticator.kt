package cash.atto.commons.gatekeeper

import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoChallenge
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoSigner
import cash.atto.commons.fromHexToByteArray
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.commons.node.custom
import cash.atto.commons.serialiazer.InstantMillisSerializer
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.remote
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

interface AttoAuthenticator {
    companion object {}

    suspend fun getAuthorization(): String
}

private val httpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }
        install(HttpTimeout)

        expectSuccess = true
    }

private class WalletGatekeeperClient(
    url: String,
    val signer: AttoSigner,
) : AttoAuthenticator {
    private val leeway = 60.seconds
    private val loginUri = "$url/login"
    private val challengeUri = "$loginUri/challenges"
    private var jwt: AttoJWT? = null

    private val mutex = Mutex()

    override suspend fun getAuthorization(): String {
        mutex.withLock {
            val jwt = this.jwt
            if (jwt == null || jwt.isExpired(leeway)) {
                return login()
            }
            return jwt.encoded
        }
    }

    private suspend fun login(): String {
        val initRequest = TokenInitRequest(AttoAlgorithm.V1, signer.publicKey)
        val initResponse =
            httpClient
                .post(loginUri) {
                    contentType(ContentType.Application.Json)
                    setBody(initRequest)
                }.body<TokenInitResponse>()

        val challenge = AttoChallenge(initResponse.challenge.fromHexToByteArray())
        val timestamp = Clock.System.now()

        val signature = signer.sign(challenge, timestamp)

        val answer = TokenInitAnswer(timestamp, signature)

        val challengeUrl = "$challengeUri/${initResponse.challenge}"
        val jwtString =
            httpClient
                .post(challengeUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(answer)
                }.body<String>()

        jwt = AttoJWT.decode(jwtString)
        return jwtString
    }

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
        @Serializable(with = InstantMillisSerializer::class)
        val timestamp: Instant,
        val signature: AttoSignature,
    )
}

fun AttoAuthenticator.Companion.custom(
    url: String,
    signer: AttoSigner,
): AttoAuthenticator {
    return WalletGatekeeperClient(url, signer)
}

fun AttoAuthenticator.Companion.attoBackend(
    network: AttoNetwork,
    signer: AttoSigner,
): AttoAuthenticator {
    val url = "https://wallet-gatekeeper.${network.name.lowercase()}.application.atto.cash"
    return WalletGatekeeperClient(url, signer)
}

fun AttoAuthenticator.toHeaderProvider(): suspend () -> Map<String, String> {
    return {
        val jwt = getAuthorization()
        mapOf("Authorization" to "Bearer $jwt")
    }
}

/**
 * Creates a AttoClient using Atto backend
 */
fun AttoNodeOperations.Companion.attoBackend(
    network: AttoNetwork,
    authenticator: AttoAuthenticator,
): AttoNodeOperations {
    val gatekeeperUrl = "https://gatekeeper.${network.name.lowercase()}.application.atto.cash"
    return AttoNodeOperations.custom(network, gatekeeperUrl, authenticator.toHeaderProvider())
}

internal fun AttoWorker.Companion.attoBackend(
    network: AttoNetwork,
    headerProvider: suspend () -> Map<String, String> = { emptyMap() },
): AttoWorker {
    val gatekeeperUrl = "https://gatekeeper.${network.name.lowercase()}.application.atto.cash"
    return AttoWorker.remote(gatekeeperUrl, headerProvider)
}

fun AttoWorker.Companion.attoBackend(
    network: AttoNetwork,
    authenticator: AttoAuthenticator,
): AttoWorker = AttoWorker.attoBackend(network, authenticator.toHeaderProvider())
