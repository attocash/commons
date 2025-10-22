package cash.atto.commons.worker

import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

actual class AttoWorkerAsyncBuilder actual constructor(
    actual val url: String,
) {
    actual var cached: Boolean = true
    actual var headers: Map<String, String> = emptyMap()
    actual var retryEvery: Duration? = null

    actual companion object {
        actual fun remote(url: String): AttoWorkerAsyncBuilder = AttoWorkerAsyncBuilder(url)
    }

    actual fun headers(value: Map<String, String>) = apply { headers = value }

    actual fun header(
        name: String,
        value: String,
    ) = apply { headers = headers + (name to value) }

    actual fun cached(value: Boolean) = apply { cached = value }

    actual fun retryEvery(value: Duration) = apply { retryEvery = value }

    actual fun retryEverySeconds(value: Long) = apply { retryEvery = value.seconds }

    actual fun build(): AttoWorkerAsync =
        AttoWorker
            .remote(url, { headers })
            .let {
                if (cached) {
                    it.cached()
                } else {
                    it
                }
            }.toAsync(Dispatchers.Default)
}
