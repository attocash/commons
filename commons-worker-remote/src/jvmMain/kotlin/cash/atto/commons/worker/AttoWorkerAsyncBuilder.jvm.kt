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
    private val url: String,
) {
    private var headers: Map<String, String> = emptyMap()
    private var cached: Boolean = true
    private var retryEvery: Duration? = null

    actual fun headers(value: Map<String, String>) = apply { headers = value }

    actual fun header(
        name: String,
        value: String,
    ) = apply { headers = headers + (name to value) }

    actual fun cached(value: Boolean) = apply { cached = value }

    actual fun retryEvery(value: Duration) = apply { retryEvery = value }

    actual fun retryEverySeconds(value: Long) = apply { retryEvery = value.seconds }

    fun retryEvery(value: java.time.Duration) = apply { retryEvery = value.toKotlinDuration() }

    fun build(dispatcher: CoroutineDispatcher): AttoWorkerAsync =
        AttoWorker
            .remote(url, { headers })
            .let {
                if (this.cached) {
                    it.cached()
                } else {
                    it
                }
            }.let {
                val retryEvery = this.retryEvery
                if (retryEvery != null) {
                    it.retry(retryEvery)
                } else {
                    it
                }
            }.toAsync(dispatcher)

    actual fun build(): AttoWorkerAsync = build(Dispatchers.Default)

    fun build(executorService: ExecutorService): AttoWorkerAsync = build(executorService.asCoroutineDispatcher())
}
