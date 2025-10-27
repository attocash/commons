package cash.atto.commons.worker

import kotlin.time.Duration

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

    companion object {
        fun remote(url: String): AttoWorkerAsyncBuilder
    }
}
