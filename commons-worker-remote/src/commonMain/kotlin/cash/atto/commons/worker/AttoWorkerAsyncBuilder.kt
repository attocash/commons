package cash.atto.commons.worker

import cash.atto.commons.utils.JsExportForJs
import kotlin.time.Duration

@JsExportForJs
expect class AttoWorkerAsyncBuilder private constructor(
    url: String,
) {
    fun headers(value: Map<String, String>): AttoWorkerAsyncBuilder

    fun header(
        name: String,
        value: String,
    ): AttoWorkerAsyncBuilder

    fun cached(value: Boolean): AttoWorkerAsyncBuilder

    fun retryEvery(value: Duration): AttoWorkerAsyncBuilder

    fun retryEverySeconds(value: Long): AttoWorkerAsyncBuilder

    fun build(): AttoWorkerAsync
}
