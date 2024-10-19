package cash.atto.commons.work

import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.gatekeeper.AttoAuthenticator
import cash.atto.commons.toHex
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes

private val httpClient = HttpClient {
    install(ContentNegotiation) {
        json()
    }
    install(HttpTimeout)
}

private class WorkerRemote(
    private val url: String,
    private val headerProvider: suspend () -> Map<String, String> = { emptyMap() }
) : AttoWorker {
    override suspend fun work(threshold: ULong, target: ByteArray): AttoWork {
        throw NotImplementedError()
    }

    override suspend fun work(network: AttoNetwork, timestamp: Instant, target: ByteArray): AttoWork {
        val headers = headerProvider.invoke()

        val uri = "$url/works"

        val request = Request(
            network = network,
            timestamp = timestamp,
            target = target.toHex()
        )

        return httpClient.post(uri) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(request)
            headers {
                headers.forEach { (key, value) -> append(key, value) }
            }
            timeout {
                socketTimeoutMillis = 5.minutes.inWholeMilliseconds
            }
        }
            .body<Response>()
            .work
    }

    override fun close() {
    }

    @Serializable
    private data class Request(
        val network: AttoNetwork,
        val timestamp: Instant,
        val target: String,
    )

    @Serializable
    private data class Response(
        val work: AttoWork,
    )
}


private class WorkerGatekeeper(
    private val urlProvider: (AttoNetwork) -> String = { "https://gatekeeper.${it.name.lowercase()}.application.atto.cash" },
    private val headerProvider: suspend () -> Map<String, String> = { emptyMap() }
) : AttoWorker {
    override suspend fun work(threshold: ULong, target: ByteArray): AttoWork {
        throw NotImplementedError()
    }

    override suspend fun work(network: AttoNetwork, timestamp: Instant, target: ByteArray): AttoWork {
        val headers = headerProvider.invoke()

        val url = urlProvider(network)
        val uri = "$url/works"

        val request = Request(
            timestamp = timestamp,
            target = target.toHex()
        )

        return httpClient.post(uri) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(request)
            headers {
                headers.forEach { (key, value) -> append(key, value) }
            }
            timeout {
                socketTimeoutMillis = 5.minutes.inWholeMilliseconds
            }
        }
            .body<Response>()
            .work
    }

    override fun close() {
    }

    @Serializable
    data class Request(
        val timestamp: Instant,
        val target: String,
    )

    @Serializable
    private data class Response(
        val work: AttoWork,
    )
}

fun AttoWorker.Companion.remote(
    url: String,
    headerProvider: suspend () -> Map<String, String> = { emptyMap() }
): AttoWorker {
    return WorkerRemote(url, headerProvider)
}

internal fun AttoWorker.Companion.attoBackend(
    urlProvider: (AttoNetwork) -> String = { "https://gatekeeper.${it.name.lowercase()}.application.atto.cash" },
    headerProvider: suspend () -> Map<String, String> = { emptyMap() }
): AttoWorker {
    return WorkerGatekeeper(urlProvider, headerProvider)
}

fun AttoWorker.Companion.attoBackend(
    authenticator: AttoAuthenticator,
): AttoWorker {
    return AttoWorker.attoBackend(
        headerProvider = {
            val jwt = authenticator.getAuthorization()
            mapOf("Authorization" to "Bearer $jwt")
        }
    )
}
