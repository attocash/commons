package cash.atto.commons.worker

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@JsExportForJs
actual class AttoWorkerAsyncBuilder actual constructor(
    private val url: String,
) {
    private var cached: Boolean = true
    private var headers: Map<String, String> = emptyMap()
    private var retryEvery: Duration? = null

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
