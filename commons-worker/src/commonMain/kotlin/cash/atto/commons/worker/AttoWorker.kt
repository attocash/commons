package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.getTarget
import cash.atto.commons.getThreshold

interface AttoWorker : AutoCloseable {
    companion object {}

    suspend fun work(
        threshold: ULong,
        target: ByteArray,
    ): AttoWork

    suspend fun work(
        network: AttoNetwork,
        timestamp: AttoInstant,
        target: ByteArray,
    ): AttoWork {
        val threshold = AttoWork.getThreshold(network, timestamp)
        return work(threshold, target)
    }

    suspend fun work(block: AttoBlock): AttoWork {
        val target = block.getTarget()
        return work(block.network, block.timestamp, target)
    }
}

private object NoOpAttoWorker : AttoWorker {
    override suspend fun work(
        threshold: ULong,
        target: ByteArray,
    ): AttoWork = throw NotImplementedError()

    override fun close() {
    }
}

fun AttoWorker.Companion.noOp(): AttoWorker = NoOpAttoWorker
