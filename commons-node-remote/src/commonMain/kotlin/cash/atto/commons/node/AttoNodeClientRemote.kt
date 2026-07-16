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
import cash.atto.commons.transport.AttoHttpTimeouts
import cash.atto.commons.transport.AttoHttpTransport
import cash.atto.commons.transport.httpStatusCodeOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
) : AttoNodeClient {
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
        transport.getNdjson(
            urlPath,
            AttoHttpTimeouts(
                request = Duration.INFINITE,
                socket = STREAM_SOCKET_IDLE_TIMEOUT,
                connect = 10.seconds,
            ),
        )

    override suspend fun account(publicKey: AttoPublicKey): AttoAccount? =
        try {
            get<AttoAccount>("accounts/$publicKey", timeout = 1.seconds)
        } catch (e: Exception) {
            if (e.httpStatusCodeOrNull() == HTTP_NOT_FOUND) {
                null
            } else {
                throw e
            }
        }

    override suspend fun account(addresses: Collection<AttoAddress>): Collection<AttoAccount> =
        post<List<AttoAccount>, AccountSearch>("accounts", AccountSearch(addresses))

    private inline fun <reified T, reified B : Any> fetchStream(
        urlPath: String,
        body: B,
        requestTimeout: Duration = Duration.INFINITE,
        socketTimeout: Duration = STREAM_SOCKET_IDLE_TIMEOUT,
        connectTimeout: Duration = 10.seconds,
    ): Flow<T> =
        transport.postNdjson(
            urlPath,
            body,
            AttoHttpTimeouts(
                request = requestTimeout,
                socket = socketTimeout,
                connect = connectTimeout,
            ),
        )

    override fun accountStream(): Flow<AttoAccount> = fetchStream("accounts/stream")

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

    override fun accountEntryStream(): Flow<AttoAccountEntry> = fetchStream("accounts/entries/stream")

    override fun accountEntryStream(hash: AttoHash): Flow<AttoAccountEntry> = fetchStream("accounts/entries/$hash/stream")

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

    override suspend fun transaction(hash: AttoHash): AttoTransaction = get("transactions/$hash")

    override fun transactionStream(): Flow<AttoTransaction> = fetchStream("transactions/stream")

    override fun transactionStream(hash: AttoHash): Flow<AttoTransaction> = fetchStream("transactions/$hash/stream")

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
        val accepted =
            fetchStream<AttoTransaction, AttoTransaction>(
                "transactions/stream",
                transaction,
                requestTimeout = 1.minutes,
                socketTimeout = 1.minutes,
                connectTimeout = 10.seconds,
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
