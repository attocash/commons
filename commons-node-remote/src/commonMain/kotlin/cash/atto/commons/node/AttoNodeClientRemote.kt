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
import cash.atto.commons.AttoVoterWeight
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val json =
    Json {
        ignoreUnknownKeys = true
    }

private val httpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout)

        expectSuccess = true
    }

private class AttoNodeClientRemote(
    private val baseUrl: String,
    private val headerProvider: suspend () -> Map<String, String> = { emptyMap() },
) : AttoNodeClient {
    private fun HttpRequestBuilder.configure(
        headersMap: Map<String, String>,
        accept: String = "application/json",
        timeout: Duration = 10.seconds,
    ) {
        contentType(ContentType.Application.Json)
        headers {
            headersMap.forEach { (key, value) -> append(key, value) }
            append("Accept", accept)
        }
        timeout {
            socketTimeoutMillis = if (timeout == Duration.INFINITE) Long.MAX_VALUE else timeout.inWholeMilliseconds
        }
    }

    private suspend inline fun <reified T> get(
        urlPath: String,
        timeout: Duration = 10.seconds,
    ): T {
        val headers = headerProvider.invoke()

        return httpClient
            .get("$baseUrl/$urlPath") {
                configure(headers, timeout = timeout)
            }.body()
    }

    private suspend inline fun <reified T> post(
        urlPath: String,
        body: Any,
        timeout: Duration = 10.seconds,
    ): T {
        val headers = headerProvider.invoke()

        return httpClient
            .post("$baseUrl/$urlPath") {
                configure(headers, timeout = timeout)
                setBody(body)
            }.body()
    }

    private inline fun <reified T> fetchStream(urlPath: String): Flow<T> =
        flow {
            val headers = headerProvider.invoke()

            httpClient
                .prepareGet("$baseUrl/$urlPath") {
                    configure(headers, accept = "application/x-ndjson", timeout = Duration.INFINITE)
                }.execute { response ->
                    response.body<ByteReadChannel>().readStream<T> { emit(it) }
                }
        }

    override suspend fun account(publicKey: AttoPublicKey): AttoAccount? =
        try {
            get<AttoAccount>("accounts/$publicKey", timeout = 1.seconds)
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                null
            } else {
                throw e
            }
        }

    override suspend fun account(addresses: Collection<AttoAddress>): Collection<AttoAccount> = post("accounts", AccountSearch(addresses))

    private inline fun <reified T> fetchStream(
        urlPath: String,
        search: Any,
    ): Flow<T> =
        flow {
            val headers = headerProvider.invoke()

            httpClient
                .preparePost("$baseUrl/$urlPath") {
                    configure(headers, accept = "application/x-ndjson", timeout = Duration.INFINITE)
                    setBody(search)
                }.execute { response ->
                    response.body<ByteReadChannel>().readStream<T> { emit(it) }
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

    override suspend fun now(currentTime: AttoInstant): TimeDifferenceResponse = get("instants/$currentTime")

    override suspend fun publish(transaction: AttoTransaction) {
        val uri = "$baseUrl/transactions/stream"
        val json = Json.encodeToString(transaction)
        val headers = headerProvider.invoke()

        val response: HttpResponse =
            httpClient.post(uri) {
                configure(headers, accept = "application/x-ndjson", timeout = 5.minutes)
                setBody(json)
            }

        val channel: ByteReadChannel = response.bodyAsChannel()
        channel.readUTF8Line()
        channel.cancel()
    }

    override suspend fun voterWeight(address: AttoAddress): AttoVoterWeight = get("vote-weights/${address.path}")
}

private suspend inline fun <reified T> ByteReadChannel.readStream(crossinline emit: suspend (T) -> Unit) {
    while (!isClosedForRead) {
        val value = readUTF8Line()
        if (value != null) {
            val item = json.decodeFromString<T>(value)
            emit(item)
        }
    }
}

@JvmSynthetic
fun AttoNodeClient.Companion.remote(
    baseUrl: String,
    headerProvider: suspend () -> Map<String, String> = { emptyMap() },
): AttoNodeClient = AttoNodeClientRemote(baseUrl, headerProvider)
