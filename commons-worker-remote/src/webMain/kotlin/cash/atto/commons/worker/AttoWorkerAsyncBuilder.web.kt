package cash.atto.commons.worker

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

    actual fun retryEvery(value: Duration) = apply { settings.retryEvery = value }

    actual fun retryEverySeconds(value: Long) = apply { settings.retryEvery = value.seconds }

    // The web builder keeps retry settings for API compatibility; retry decoration is JVM-only.
    actual fun build(): AttoWorkerAsync = settings.createWorker().toAsync(Dispatchers.Default)
}
