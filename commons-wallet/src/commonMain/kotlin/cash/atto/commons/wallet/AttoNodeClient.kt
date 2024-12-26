package cash.atto.commons.wallet

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.gatekeeper.AttoAuthenticator
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface AttoNodeClient {
    companion object {}

    val network: AttoNetwork

    suspend fun account(publicKey: AttoPublicKey): AttoAccount?

    fun accountStream(publicKey: AttoPublicKey): Flow<AttoAccount>

    fun accountEntryStream(
        publicKey: AttoPublicKey,
        fromHeight: AttoHeight = AttoHeight(1UL),
    ): Flow<AttoAccountEntry>

    fun receivableStream(publicKey: AttoPublicKey): Flow<AttoReceivable>

    fun transactionStream(
        publicKey: AttoPublicKey,
        fromHeight: AttoHeight = AttoHeight(1UL),
    ): Flow<AttoTransaction>

    suspend fun now(): Instant

    suspend fun publish(transaction: AttoTransaction)
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

private class NodeClient(
    override val network: AttoNetwork,
    private val url: String,
    private val headerProvider: suspend () -> Map<String, String> = { emptyMap() },
) : AttoNodeClient {
    private val logger = KotlinLogging.logger {}

    private val retryDelay = 10.seconds

    override suspend fun account(publicKey: AttoPublicKey): AttoAccount? {
        val uri = "$url/accounts/$publicKey"
        val headers = headerProvider.invoke()

        return try {
            httpClient
                .get(uri) {
                    contentType(ContentType.Application.Json)
                    headers {
                        headers.forEach { (key, value) -> append(key, value) }
                        append("Accept", "application/json")
                    }
                    timeout {
                        socketTimeoutMillis = 1.seconds.inWholeMilliseconds
                    }
                }.body<AttoAccount?>()
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                null
            } else {
                throw e
            }
        }
    }

    private inline fun <reified T> fetchStream(urlPath: String): Flow<T> {
        return flow {
            while (coroutineContext.isActive) {
                try {
                    val headers = headerProvider.invoke()

                    httpClient
                        .prepareGet("$url/$urlPath") {
                            timeout {
                                socketTimeoutMillis = Long.MAX_VALUE
                            }
                            headers {
                                headers.forEach { (key, value) -> append(key, value) }
                                append("Accept", "application/x-ndjson")
                            }
                        }.execute { response ->
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

    override fun accountStream(publicKey: AttoPublicKey): Flow<AttoAccount> {
        return fetchStream("accounts/$publicKey/stream")
    }

    override fun receivableStream(publicKey: AttoPublicKey): Flow<AttoReceivable> {
        return fetchStream("accounts/$publicKey/receivables/stream")
    }

    override fun accountEntryStream(
        publicKey: AttoPublicKey,
        fromHeight: AttoHeight,
    ): Flow<AttoAccountEntry> {
        return fetchStream("accounts/$publicKey/entries/stream?fromHeight=$fromHeight")
    }

    override fun transactionStream(
        publicKey: AttoPublicKey,
        fromHeight: AttoHeight,
    ): Flow<AttoTransaction> {
        return fetchStream("accounts/$publicKey/transactions/stream?fromHeight=$fromHeight")
    }

    override suspend fun publish(transaction: AttoTransaction) {
        val uri = "$url/transactions/stream"
        val json = Json.encodeToString(transaction)
        val headers = headerProvider.invoke()

        val response: HttpResponse =
            httpClient.post(uri) {
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

    override suspend fun now(): Instant {
        val diff =
            httpClient
                .get("$url/instants/${Clock.System.now()}")
                .body<InstantResponse>()
                .differenceMillis
        return Clock.System.now().plus(diff.milliseconds)
    }

    @Serializable
    data class InstantResponse(
        val clientInstant: Instant,
        val serverInstant: Instant,
        val differenceMillis: Long,
    )
}

fun AttoNodeClient.Companion.custom(
    network: AttoNetwork,
    url: String,
    headerProvider: suspend () -> Map<String, String>,
): AttoNodeClient {
    return NodeClient(network, url, headerProvider)
}

internal fun AttoNodeClient.Companion.attoBackend(
    network: AttoNetwork,
    gatekeeperUrl: String,
    authenticator: AttoAuthenticator,
): AttoNodeClient {
    return AttoNodeClient.custom(network, gatekeeperUrl) {
        val jwt = authenticator.getAuthorization()
        mapOf("Authorization" to "Bearer $jwt")
    }
}

/**
 * Creates a AttoClient using Atto backend
 */
fun AttoNodeClient.Companion.attoBackend(
    network: AttoNetwork,
    authenticator: AttoAuthenticator,
): AttoNodeClient {
    val gatekeeperUrl = "https://gatekeeper.${network.name.lowercase()}.application.atto.cash"
    return AttoNodeClient.attoBackend(network, gatekeeperUrl, authenticator)
}
