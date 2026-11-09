package cash.atto.commons.worker

import kotlin.time.Duration

internal class AttoRemoteWorkerSettings(
    private val url: String,
) {
    var headers: Map<String, String> = emptyMap()
    var cached: Boolean = true
    var timeout: Duration = DEFAULT_TIMEOUT
    var retryEvery: Duration? = null

    fun createWorker(): AttoWorker {
        val remote = AttoWorker.remote(url, timeout, headerProvider = { headers })
        return if (cached) remote.cached() else remote
    }
}
