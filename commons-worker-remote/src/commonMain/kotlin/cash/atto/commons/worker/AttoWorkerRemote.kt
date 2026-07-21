package cash.atto.commons.worker

import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.fromHexToByteArray
import cash.atto.commons.transport.AttoHttpTimeouts
import cash.atto.commons.transport.AttoHttpTransport
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal val DEFAULT_TIMEOUT: Duration = 5.minutes

private class WorkerRemote(
    url: String,
    private val timeout: Duration = DEFAULT_TIMEOUT,
    headerProvider: suspend () -> Map<String, String> = { emptyMap() },
) : AttoWorkerOperations {
    private val transport = AttoHttpTransport(url, headerProvider)

    override suspend fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoWork = throw NotImplementedError()

    override suspend fun work(request: AttoWorkerOperations.Request): AttoWorkerOperations.Response {
        val response =
            transport.post<AttoWorkerOperations.Request, AttoWorkerOperations.Response>(
                path = "works",
                body = request,
                timeouts = AttoHttpTimeouts(socket = timeout),
            )

        val target = AttoWorkTarget(request.target.fromHexToByteArray())
        require(AttoWork.isValid(request.network, request.timestamp, target, response.work.value)) {
            "Remote worker returned invalid work for network=${request.network} timestamp=${request.timestamp} target=${request.target}"
        }
        return response
    }

    override fun close() {
    }
}

fun AttoWorker.Companion.remote(
    url: String,
    timeout: Duration = DEFAULT_TIMEOUT,
    headerProvider: suspend () -> Map<String, String> = { emptyMap() },
): AttoWorker = WorkerRemote(url, timeout, headerProvider)
