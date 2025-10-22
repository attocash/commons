package cash.atto.commons.worker

import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.toHex
import kotlinx.serialization.Serializable

interface AttoWorkerOperations : AttoWorker {
    suspend fun work(request: Request): Response

    override suspend fun work(
        network: AttoNetwork,
        timestamp: AttoInstant,
        target: AttoWorkTarget,
    ): AttoWork {
        val request = Request(network, timestamp, target.value.toHex())
        val response = work(request)
        return response.work
    }

    @Serializable
    data class Request(
        val network: AttoNetwork,
        val timestamp: AttoInstant,
        val target: String,
    )

    @Serializable
    data class Response(
        val work: AttoWork,
    )
}
