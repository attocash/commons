package cash.atto.commons.worker

import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
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
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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

internal val DEFAULT_TIMEOUT: Duration = 5.minutes

private fun Duration.toTimeoutMillis(): Long = if (this == Duration.INFINITE) Long.MAX_VALUE else inWholeMilliseconds

private class WorkerRemote(
    private val url: String,
    private val timeout: Duration = DEFAULT_TIMEOUT,
    private val headerProvider: suspend () -> Map<String, String> = { emptyMap() },
) : AttoWorkerOperations {
    override suspend fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoWork = throw NotImplementedError()

    override suspend fun work(request: AttoWorkerOperations.Request): AttoWorkerOperations.Response {
        val headers = headerProvider.invoke()

        val uri = "$url/works"

        return httpClient
            .post(uri) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(request)
                headers {
                    headers.forEach { (key, value) -> append(key, value) }
                }
                timeout {
                    socketTimeoutMillis = timeout.toTimeoutMillis()
                }
            }.body<AttoWorkerOperations.Response>()
    }

    override fun close() {
    }
}

fun AttoWorker.Companion.remote(
    url: String,
    timeout: Duration = DEFAULT_TIMEOUT,
    headerProvider: suspend () -> Map<String, String> = { emptyMap() },
): AttoWorker = WorkerRemote(url, timeout, headerProvider)
