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

private class WorkerRemote(
    private val url: String,
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
                    socketTimeoutMillis = 5.minutes.inWholeMilliseconds
                }
            }.body<AttoWorkerOperations.Response>()
    }

    override fun close() {
    }
}

fun AttoWorker.Companion.remote(
    url: String,
    headerProvider: suspend () -> Map<String, String> = { emptyMap() },
): AttoWorker = WorkerRemote(url, headerProvider)
