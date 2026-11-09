package cash.atto.commons.worker

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

@JsExportForJs
actual class AttoWorkerAsyncBuilder actual constructor(
    url: String,
) {
    private val settings = AttoRemoteWorkerSettings(url)

    actual fun headers(value: Map<String, String>) = apply { settings.headers = value }

    actual fun header(
        name: String,
        value: String,
    ) = apply { settings.headers = settings.headers + (name to value) }

    actual fun cached(value: Boolean) = apply { settings.cached = value }

    actual fun timeout(value: Duration) = apply { settings.timeout = value }

    actual fun timeoutSeconds(value: Long) = apply { settings.timeout = value.seconds }

    fun timeout(value: java.time.Duration) = apply { settings.timeout = value.toKotlinDuration() }

    actual fun retryEvery(value: Duration) = apply { settings.retryEvery = value }

    actual fun retryEverySeconds(value: Long) = apply { settings.retryEvery = value.seconds }

    fun retryEvery(value: java.time.Duration) = apply { settings.retryEvery = value.toKotlinDuration() }

    fun build(dispatcher: CoroutineDispatcher): AttoWorkerAsync =
        settings
            .createWorker()
            .let {
                val retryEvery = settings.retryEvery
                if (retryEvery != null) {
                    it.retry(retryEvery)
                } else {
                    it
                }
            }.toAsync(dispatcher)

    actual fun build(): AttoWorkerAsync = build(Dispatchers.Default)

    fun build(executorService: ExecutorService): AttoWorkerAsync = build(executorService.asCoroutineDispatcher())
}
