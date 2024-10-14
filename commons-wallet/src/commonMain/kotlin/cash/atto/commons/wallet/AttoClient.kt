package cash.atto.commons.wallet

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoSigner
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoWork
import cash.atto.commons.fromHexToByteArray
import cash.atto.commons.toHex
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface AttoClient {
    companion object {}

    val network: AttoNetwork
    fun accounts(publicKey: AttoPublicKey): Flow<AttoAccount>
    fun receivables(publicKey: AttoPublicKey): Flow<AttoReceivable>
    fun transactions(publicKey: AttoPublicKey, fromHeight: AttoHeight = AttoHeight(1UL)): Flow<AttoTransaction>
    suspend fun now(): Instant
    suspend fun publish(transaction: AttoTransaction)
    suspend fun work(timestamp: Instant, target: ByteArray): AttoWork {
        throw NotImplementedError("Work is not implemented")
    }
}

private val httpClient = HttpClient {
    install(ContentNegotiation) {
        json()
    }
    install(HttpTimeout)
}

internal class AttoAuthenticatorClient(val signer: AttoSigner, url: String) {
    private val leeway = 60.seconds
    private val loginUri = "$url/login"
    private val challengeUri = "$loginUri/challenges"
    private var jwt: AttoJWT? = null

    private val mutex = Mutex()

    suspend fun getAuthorization(): String {
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
        val initResponse = httpClient.post(loginUri) {
            contentType(ContentType.Application.Json)
            setBody(initRequest)
        }.body<TokenInitResponse>()


        val challenge = initResponse.challenge.fromHexToByteArray()
        val hash = AttoHash.hash(64, signer.publicKey.value, challenge)

        val signature = signer.sign(hash)
        val answer = TokenInitAnswer(signature)

        val challengeUrl = "$challengeUri/${initResponse.challenge}"
        val jwtString = httpClient.post(challengeUrl) {
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
        val signature: AttoSignature,
    )

}

internal class AttoSimpleClient(
    override val network: AttoNetwork,
    private val url: String,
    private val headerProvider: suspend () -> Map<String, String>
) : AttoClient {
    private val logger = KotlinLogging.logger {}

    private val retryDelay = 10.seconds

    private inline fun <reified T> fetchStream(urlPath: String): Flow<T> {
        return flow {
            while (coroutineContext.isActive) {
                try {
                    val headers = headerProvider.invoke()

                    httpClient.prepareGet("$url/$urlPath") {
                        timeout {
                            socketTimeoutMillis = Long.MAX_VALUE
                        }
                        headers {
                            headers.forEach { (key, value) -> append(key, value) }
                            append("Accept", "application/x-ndjson")
                        }
                    }
                        .execute { response ->
                            val channel: ByteReadChannel = response.body()
                            while (!channel.isClosedForRead) {
                                val json = channel.readUTF8Line()
                                if (json != null) {
                                    val item = Json.decodeFromString<T>(json)
                                    emit(item)
                                }
                            }
                        }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to stream $urlPath. Retrying in $retryDelay..." }
                }

                if (coroutineContext.isActive) {
                    delay(retryDelay)
                }
            }
        }
    }

    override fun accounts(publicKey: AttoPublicKey): Flow<AttoAccount> {
        return fetchStream("accounts/$publicKey/stream")
    }

    override fun receivables(publicKey: AttoPublicKey): Flow<AttoReceivable> {
        return fetchStream("accounts/$publicKey/receivables/stream")
    }


    override fun transactions(publicKey: AttoPublicKey, fromHeight: AttoHeight): Flow<AttoTransaction> {
        return fetchStream("accounts/$publicKey/transactions/stream?fromHeight=$fromHeight")
    }

    override suspend fun publish(transaction: AttoTransaction) {
        val uri = "$url/transactions/stream"
        val json = Json.encodeToString(transaction)
        val headers = headerProvider.invoke()

        val response: HttpResponse = httpClient.post(uri) {
            contentType(ContentType.Application.Json)
            setBody(json)
            headers {
                headers.forEach { (key, value) -> append(key, value) }
                append("Accept", "application/x-ndjson")
            }
            timeout {
                socketTimeoutMillis = 5.minutes.inWholeMilliseconds
            }
        }

        val channel: ByteReadChannel = response.bodyAsChannel()
        channel.readUTF8Line()
        channel.cancel()
    }

    override suspend fun work(timestamp: Instant, target: ByteArray): AttoWork {
        val headers = headerProvider.invoke()

        val uri = "$url/works"

        val request = WorkRequest(
            timestamp = timestamp,
            target = target.toHex()
        )

        return httpClient.post(uri) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(request)
            headers {
                headers.forEach { (key, value) -> append(key, value) }
                append("Accept", "application/x-ndjson")
            }
            timeout {
                socketTimeoutMillis = 5.minutes.inWholeMilliseconds
            }
        }
            .body<WorkResponse>()
            .work
    }

    override suspend fun now(): Instant {
        val diff = httpClient.get("$url/instants/${Clock.System.now()}")
            .body<InstantResponse>()
            .differenceMillis
        return Clock.System.now().plus(diff.milliseconds)
    }

    @Serializable
    data class WorkRequest(
        val timestamp: Instant,
        val target: String,
    )

    @Serializable
    data class WorkResponse(
        val work: AttoWork,
    )

    @Serializable
    data class InstantResponse(
        val clientInstant: Instant,
        val serverInstant: Instant,
        val differenceMillis: Long,
    )
}

fun AttoClient.Companion.create(
    network: AttoNetwork,
    url: String,
    headerProvider: suspend () -> Map<String, String>
): AttoClient {
    return AttoSimpleClient(network, url, headerProvider)
}

internal fun AttoClient.Companion.createAtto(
    network: AttoNetwork,
    signer: AttoSigner,
    gatekeeperUrl: String,
    walletGatekeeperUrl: String
): AttoClient {
    val authenticatorClient = AttoAuthenticatorClient(signer, walletGatekeeperUrl)
    return AttoClient.create(network, gatekeeperUrl) {
        val jwt = authenticatorClient.getAuthorization()
        mapOf("Authorization" to "Bearer $jwt")
    }
}

/**
 * Creates a AttoClient using Atto backend
 */
fun AttoClient.Companion.createAtto(network: AttoNetwork, signer: AttoSigner): AttoClient {
    val gatekeeperUrl = "https://gatekeeper.${network.name.lowercase()}.application.atto.cash"
    val walletGatekeeperUrl = "https://wallet-gatekeeper.${network.name.lowercase()}.application.atto.cash"
    return AttoClient.createAtto(network, signer, gatekeeperUrl, walletGatekeeperUrl)
}


