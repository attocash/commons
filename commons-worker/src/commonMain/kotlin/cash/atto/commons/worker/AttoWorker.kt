package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.getTarget
import cash.atto.commons.getThreshold

interface AttoWorker : AutoCloseable {
    companion object {}

    suspend fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoWork

    suspend fun work(
        network: AttoNetwork,
        timestamp: AttoInstant,
        target: AttoWorkTarget,
    ): AttoWork {
        val threshold = AttoWork.getThreshold(network, timestamp)
        return work(threshold, target)
    }

    suspend fun work(block: AttoBlock): AttoWork {
        val target = block.getTarget()
        return work(block.network, block.timestamp, target)
    }
}
