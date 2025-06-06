package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoOpenBlock
import cash.atto.commons.AttoWork
import cash.atto.commons.PreviousSupport
import cash.atto.commons.getThreshold
import kotlinx.datetime.Instant

interface AttoWorker : AutoCloseable {
    companion object {}

    suspend fun work(
        threshold: ULong,
        target: ByteArray,
    ): AttoWork

    suspend fun work(
        network: AttoNetwork,
        timestamp: Instant,
        target: ByteArray,
    ): AttoWork {
        val threshold = AttoWork.getThreshold(network, timestamp)
        return work(threshold, target)
    }

    suspend fun work(block: AttoBlock): AttoWork {
        val target =
            when (block) {
                is AttoOpenBlock -> block.publicKey.value
                is PreviousSupport -> block.previous.value
                else -> throw IllegalArgumentException("Unsupported block type $block")
            }
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
