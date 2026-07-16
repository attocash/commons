package cash.atto.commons.transport

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLineStrict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlin.time.Duration

data class AttoHttpTimeouts(
    val request: Duration? = null,
    val socket: Duration? = null,
    val connect: Duration? = null,
)

class AttoHttpTransport(
    @PublishedApi internal val baseUrl: String,
    @PublishedApi internal val headerProvider: suspend () -> Map<String, String> = { emptyMap() },
) {
    @PublishedApi
    internal var client: HttpClient = sharedHttpClient

    suspend inline fun <reified Response> get(
        path: String,
        timeouts: AttoHttpTimeouts = AttoHttpTimeouts(),
    ): Response =
        client
            .get(url(path)) {
                configure(headerProvider(), ContentType.Application.Json, timeouts)
            }.body()

    suspend inline fun <reified Request : Any, reified Response> post(
        path: String,
        body: Request,
        timeouts: AttoHttpTimeouts = AttoHttpTimeouts(),
    ): Response =
        client
            .post(url(path)) {
                configure(headerProvider(), ContentType.Application.Json, timeouts)
                setBody(body)
            }.body()

    inline fun <reified Response> getNdjson(
        path: String,
        timeouts: AttoHttpTimeouts = AttoHttpTimeouts(),
    ): Flow<Response> =
        channelFlow {
            client
                .prepareGet(url(path)) {
                    configure(headerProvider(), NDJSON_CONTENT_TYPE, timeouts)
                }.execute { response ->
                    response.body<ByteReadChannel>().readNdjson<Response> { send(it) }
                }
        }

    inline fun <reified Request : Any, reified Response> postNdjson(
        path: String,
        body: Request,
        timeouts: AttoHttpTimeouts = AttoHttpTimeouts(),
    ): Flow<Response> =
        channelFlow {
            client
                .preparePost(url(path)) {
                    configure(headerProvider(), NDJSON_CONTENT_TYPE, timeouts)
                    setBody(body)
                }.execute { response ->
                    response.body<ByteReadChannel>().readNdjson<Response> { send(it) }
                }
        }

    @PublishedApi
    internal fun url(path: String): String = "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
}

fun Throwable.httpStatusCodeOrNull(): Int? =
    when (this) {
        is ClientRequestException -> response.status.value
        is ServerResponseException -> response.status.value
        else -> cause?.httpStatusCodeOrNull()
    }

@PublishedApi
internal val transportJson =
    Json {
        ignoreUnknownKeys = true
    }

private val sharedHttpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(transportJson)
        }
        install(HttpTimeout)
        expectSuccess = true
    }

@PublishedApi
internal val NDJSON_CONTENT_TYPE = ContentType.parse("application/x-ndjson")

@PublishedApi
internal const val NDJSON_MAX_LINE_CHARS: Long = 2_000L

@PublishedApi
internal fun HttpRequestBuilder.configure(
    headersMap: Map<String, String>,
    accept: ContentType,
    timeouts: AttoHttpTimeouts,
) {
    contentType(ContentType.Application.Json)
    headers {
        headersMap.forEach { (key, value) -> append(key, value) }
        append("Accept", accept.toString())
    }
    timeout {
        connectTimeoutMillis = timeouts.connect.toTimeoutMillisOrNull()
        requestTimeoutMillis = timeouts.request.toTimeoutMillisOrNull()
        socketTimeoutMillis = timeouts.socket.toTimeoutMillisOrNull()
    }
}

@PublishedApi
internal fun Duration?.toTimeoutMillisOrNull(): Long? =
    when (this) {
        null -> null
        Duration.INFINITE -> Long.MAX_VALUE
        else -> inWholeMilliseconds
    }

@PublishedApi
internal suspend inline fun <reified Response> ByteReadChannel.readNdjson(crossinline emit: suspend (Response) -> Unit) {
    while (!isClosedForRead) {
        val line = readLineStrict(NDJSON_MAX_LINE_CHARS)
        if (line != null) {
            emit(transportJson.decodeFromString<Response>(line))
        }
    }
}
