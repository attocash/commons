package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoVoterWeight
import cash.atto.commons.transport.AttoHttpTimeouts
import cash.atto.commons.transport.AttoHttpTransport
import cash.atto.commons.transport.AttoWebSocketTransport
import cash.atto.commons.transport.httpStatusCodeOrNull
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val STREAM_SOCKET_IDLE_TIMEOUT: Duration = 1.hours
private const val HTTP_NOT_FOUND = 404

private class AttoNodeClientRemote(
    baseUrl: String,
    headerProvider: suspend () -> Map<String, String> = { emptyMap() },
    private val streamTransport: AttoNodeStreamTransport = AttoNodeStreamTransport.HTTP,
) : AttoNodeClient {
    private val webSocketTransport = AttoWebSocketTransport(baseUrl, headerProvider)
    private val json = Json { ignoreUnknownKeys = true }
    private val socketStreams = AttoNodeWebSocketStreams(webSocketTransport, json)
    private val transport = AttoHttpTransport(baseUrl, headerProvider)

    private suspend inline fun <reified T> get(
        urlPath: String,
        timeout: Duration = 10.seconds,
    ): T =
        transport.get(
            urlPath,
            AttoHttpTimeouts(
                request = timeout,
                socket = timeout,
                connect = timeout,
            ),
        )

    private suspend inline fun <reified T, reified B : Any> post(
        urlPath: String,
        body: B,
        timeout: Duration = 10.seconds,
    ): T =
        transport.post(
            urlPath,
            body,
            AttoHttpTimeouts(
                request = timeout,
                socket = timeout,
                connect = timeout,
            ),
        )

    private inline fun <reified T> fetchStream(urlPath: String): Flow<T> =
        if (streamTransport == AttoNodeStreamTransport.WEBSOCKET) {
            webSocketStream(AttoStreamRequest(AttoStreamMethod.GET, "/v2/$urlPath"))
        } else {
            transport.getNdjson(
                "v2/$urlPath",
                AttoHttpTimeouts(
                    request = Duration.INFINITE,
                    socket = STREAM_SOCKET_IDLE_TIMEOUT,
                    connect = 10.seconds,
                ),
            )
        }

    override suspend fun account(address: AttoAddress): AttoAccount? =
        try {
            get<AttoAccount>("v2/accounts/${address.toString().encodeURLParameter()}", timeout = 1.seconds)
        } catch (e: Exception) {
            if (e.httpStatusCodeOrNull() == HTTP_NOT_FOUND) {
                null
            } else {
                throw e
            }
        }

    override suspend fun account(addresses: Collection<AttoAddress>): Collection<AttoAccount> =
        post<List<AttoAccount>, AccountSearch>("v2/accounts", AccountSearch(addresses))

    private inline fun <reified T, reified B : Any> fetchStream(
        urlPath: String,
        body: B,
        requestTimeout: Duration = Duration.INFINITE,
        socketTimeout: Duration = STREAM_SOCKET_IDLE_TIMEOUT,
        connectTimeout: Duration = 10.seconds,
    ): Flow<T> =
        if (streamTransport == AttoNodeStreamTransport.WEBSOCKET) {
            webSocketStream(
                AttoStreamRequest(
                    AttoStreamMethod.POST,
                    "/v2/$urlPath",
                    json.encodeToJsonElement(kotlinx.serialization.serializer<B>(), body),
                ),
                requestTimeout,
                socketTimeout,
                connectTimeout,
            )
        } else {
            transport.postNdjson(
                "v2/$urlPath",
                body,
                AttoHttpTimeouts(
                    request = requestTimeout,
                    socket = socketTimeout,
                    connect = connectTimeout,
                ),
            )
        }

    private inline fun <reified T> webSocketStream(
        request: AttoStreamRequest,
        requestTimeout: Duration = Duration.INFINITE,
        idleTimeout: Duration = STREAM_SOCKET_IDLE_TIMEOUT,
        connectTimeout: Duration = 10.seconds,
    ): Flow<T> =
        socketStreams.stream(kotlinx.serialization.serializer<T>(), flowOf(request), false, requestTimeout, idleTimeout, connectTimeout)

    override val supportsSubscriptionUpdates: Boolean get() = streamTransport == AttoNodeStreamTransport.WEBSOCKET

    override fun accountStream(addresses: Flow<Collection<AttoAddress>>): Flow<AttoAccount> =
        if (!supportsSubscriptionUpdates) {
            super.accountStream(
                addresses,
            )
        } else {
            dynamicStream("accounts/stream", addresses.map { AccountSearch(it) })
        }

    override fun receivableStream(
        addresses: Flow<Collection<AttoAddress>>,
        minAmount: AttoAmount,
    ): Flow<AttoReceivable> =
        if (!supportsSubscriptionUpdates) {
            super.receivableStream(addresses, minAmount)
        } else {
            dynamicStream("accounts/receivables/stream?minAmount=$minAmount", addresses.map { AccountSearch(it) })
        }

    override fun accountEntryStream(searches: Flow<HeightSearch>): Flow<AttoAccountEntry> =
        if (!supportsSubscriptionUpdates) super.accountEntryStream(searches) else dynamicStream("accounts/entries/stream", searches)

    override fun transactionStream(searches: Flow<HeightSearch>): Flow<AttoTransaction> =
        if (!supportsSubscriptionUpdates) super.transactionStream(searches) else dynamicStream("accounts/transactions/stream", searches)

    private inline fun <reified T, reified B> dynamicStream(
        path: String,
        bodies: Flow<B>,
    ): Flow<T> =
        socketStreams.stream(
            kotlinx.serialization.serializer<T>(),
            bodies.map {
                AttoStreamRequest(
                    AttoStreamMethod.POST,
                    "/v2/$path",
                    json.encodeToJsonElement(kotlinx.serialization.serializer<B>(), it),
                )
            },
            true,
        )

    override fun accountStream(): Flow<AttoAccount> = fetchStream("accounts/stream")

    override fun accountStream(address: AttoAddress): Flow<AttoAccount> =
        fetchStream("accounts/${address.toString().encodeURLParameter()}/stream")

    override fun accountStream(addresses: Collection<AttoAddress>): Flow<AttoAccount> =
        fetchStream("accounts/stream", AccountSearch(addresses))

    override fun receivableStream(
        address: AttoAddress,
        minAmount: AttoAmount,
    ): Flow<AttoReceivable> = fetchStream("accounts/${address.toString().encodeURLParameter()}/receivables/stream?minAmount=$minAmount")

    override fun receivableStream(
        addresses: Collection<AttoAddress>,
        minAmount: AttoAmount,
    ): Flow<AttoReceivable> = fetchStream("accounts/receivables/stream?minAmount=$minAmount", AccountSearch(addresses))

    override suspend fun accountEntry(hash: AttoHash): AttoAccountEntry =
        fetchStream<AttoAccountEntry>("accounts/entries/$hash/stream").first()

    override fun accountEntryStream(): Flow<AttoAccountEntry> = fetchStream("accounts/entries/stream")

    override fun accountEntryStream(hash: AttoHash): Flow<AttoAccountEntry> = fetchStream("accounts/entries/$hash/stream")

    override fun accountEntryStream(
        address: AttoAddress,
        fromHeight: AttoHeight,
        toHeight: AttoHeight?,
    ): Flow<AttoAccountEntry> {
        val queryParams = heightQuery(fromHeight, toHeight)
        return fetchStream("accounts/${address.toString().encodeURLParameter()}/entries/stream?$queryParams")
    }

    override fun accountEntryStream(search: HeightSearch): Flow<AttoAccountEntry> = fetchStream("accounts/entries/stream", search)

    override suspend fun transaction(hash: AttoHash): AttoTransaction = get("transactions/$hash")

    override fun transactionStream(): Flow<AttoTransaction> = fetchStream("transactions/stream")

    override fun transactionStream(hash: AttoHash): Flow<AttoTransaction> = fetchStream("transactions/$hash/stream")

    override fun transactionStream(
        address: AttoAddress,
        fromHeight: AttoHeight,
        toHeight: AttoHeight?,
    ): Flow<AttoTransaction> {
        val queryParams = heightQuery(fromHeight, toHeight)
        return fetchStream("accounts/${address.toString().encodeURLParameter()}/transactions/stream?$queryParams")
    }

    override fun transactionStream(search: HeightSearch): Flow<AttoTransaction> = fetchStream("accounts/transactions/stream", search)

    private fun heightQuery(
        fromHeight: AttoHeight,
        toHeight: AttoHeight?,
    ): String =
        buildString {
            append("fromHeight=$fromHeight")
            if (toHeight != null) {
                append("&toHeight=$toHeight")
            }
        }

    override suspend fun now(currentTime: AttoInstant): TimeDifferenceResponse = get("instants/$currentTime")

    override suspend fun publish(transaction: AttoTransaction) {
        val accepted =
            transport
                .postNdjson<AttoTransaction, AttoTransaction>(
                    "v2/transactions",
                    transaction,
                    AttoHttpTimeouts(
                        request = 1.minutes,
                        socket = 1.minutes,
                        connect = 10.seconds,
                    ),
                ).first()

        requireMatchingPublish(transaction, accepted)
    }

    override suspend fun voterWeight(address: AttoAddress): AttoVoterWeight = get("vote-weights/${address.path}")
}

@JvmSynthetic
fun AttoNodeClient.Companion.remote(
    baseUrl: String,
    headerProvider: suspend () -> Map<String, String> = { emptyMap() },
): AttoNodeClient = AttoNodeClientRemote(baseUrl, headerProvider)

@JvmSynthetic
fun AttoNodeClient.Companion.remote(
    baseUrl: String,
    streamTransport: AttoNodeStreamTransport,
    headerProvider: suspend () -> Map<String, String> = { emptyMap() },
): AttoNodeClient = AttoNodeClientRemote(baseUrl, headerProvider, streamTransport)
