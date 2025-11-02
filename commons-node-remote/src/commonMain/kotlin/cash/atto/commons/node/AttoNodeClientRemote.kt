package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
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
import io.ktor.client.request.preparePost
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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

private class AttoNodeClientRemote(
    private val baseUrl: String,
    private val headerProvider: suspend () -> Map<String, String> = { emptyMap() },
) : AttoNodeClient {
    override suspend fun account(publicKey: AttoPublicKey): AttoAccount? {
        val uri = "$baseUrl/accounts/$publicKey"
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

    override suspend fun account(addresses: Collection<AttoAddress>): Collection<AttoAccount> {
        val uri = "$baseUrl/accounts"
        val headers = headerProvider.invoke()
        val search = AccountSearch(addresses)

        return httpClient
            .post(uri) {
                contentType(ContentType.Application.Json)
                setBody(search)
                headers {
                    headers.forEach { (key, value) -> append(key, value) }
                    append("Accept", "application/json")
                }
                timeout {
                    socketTimeoutMillis = 10.seconds.inWholeMilliseconds
                }
            }.body()
    }

    private inline fun <reified T> fetchStream(urlPath: String): Flow<T> =
        flow {
            val headers = headerProvider.invoke()

            httpClient
                .prepareGet("$baseUrl/$urlPath") {
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
        }

    private inline fun <reified T> fetchStream(
        urlPath: String,
        search: Any,
    ): Flow<T> =
        flow {
            val headers = headerProvider.invoke()

            httpClient
                .preparePost("$baseUrl/$urlPath") {
                    timeout {
                        socketTimeoutMillis = Long.MAX_VALUE
                    }
                    headers {
                        headers.forEach { (key, value) -> append(key, value) }
                        append("Accept", "application/x-ndjson")
                    }
                    contentType(ContentType.Application.Json)
                    setBody(search)
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
        }

    override fun accountStream(publicKey: AttoPublicKey): Flow<AttoAccount> = fetchStream("accounts/$publicKey/stream")

    override fun accountStream(addresses: Collection<AttoAddress>): Flow<AttoAccount> =
        fetchStream("accounts/stream", AccountSearch(addresses))

    override fun receivableStream(
        publicKey: AttoPublicKey,
        minAmount: AttoAmount,
    ): Flow<AttoReceivable> = fetchStream("accounts/$publicKey/receivables/stream?minAmount=$minAmount")

    override fun receivableStream(
        addresses: Collection<AttoAddress>,
        minAmount: AttoAmount,
    ): Flow<AttoReceivable> = fetchStream("accounts/receivables/stream?minAmount=$minAmount", AccountSearch(addresses))

    override suspend fun accountEntry(hash: AttoHash): AttoAccountEntry =
        fetchStream<AttoAccountEntry>("accounts/entries/$hash/stream").first()

    override fun accountEntryStream(
        publicKey: AttoPublicKey,
        fromHeight: AttoHeight,
        toHeight: AttoHeight?,
    ): Flow<AttoAccountEntry> {
        val queryParams =
            buildString {
                append("fromHeight=$fromHeight")
                if (toHeight != null) {
                    append("&toHeight=$toHeight")
                }
            }
        return fetchStream("accounts/$publicKey/entries/stream?$queryParams")
    }

    override fun accountEntryStream(search: HeightSearch): Flow<AttoAccountEntry> = fetchStream("accounts/entries/stream", search)

    override suspend fun transaction(hash: AttoHash): AttoTransaction = fetchStream<AttoTransaction>("transactions/$hash/stream").first()

    override fun transactionStream(
        publicKey: AttoPublicKey,
        fromHeight: AttoHeight,
        toHeight: AttoHeight?,
    ): Flow<AttoTransaction> {
        val queryParams =
            buildString {
                append("fromHeight=$fromHeight")
                if (toHeight != null) {
                    append("&toHeight=$toHeight")
                }
            }
        return fetchStream("accounts/$publicKey/transactions/stream?$queryParams")
    }

    override fun transactionStream(search: HeightSearch): Flow<AttoTransaction> = fetchStream("accounts/transactions/stream", search)

    override suspend fun now(currentTime: AttoInstant): TimeDifferenceResponse {
        val uri = "$baseUrl/instants/$currentTime"
        val headers = headerProvider.invoke()

        return httpClient
            .get(uri) {
                contentType(ContentType.Application.Json)
                headers {
                    headers.forEach { (key, value) -> append(key, value) }
                    append("Accept", "application/json")
                }
                timeout {
                    socketTimeoutMillis = 10.seconds.inWholeMilliseconds
                }
            }.body()
    }

    override suspend fun publish(transaction: AttoTransaction) {
        val uri = "$baseUrl/transactions/stream"
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
}

@JvmSynthetic
fun AttoNodeClient.Companion.remote(
    baseUrl: String,
    headerProvider: suspend () -> Map<String, String> = { emptyMap() },
): AttoNodeClient = AttoNodeClientRemote(baseUrl, headerProvider)
