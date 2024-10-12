package cash.atto.commons.work

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

    suspend fun work(block: AttoOpenBlock): AttoWork {
        return work(block.network, block.timestamp, block.publicKey.value)
    }

    suspend fun <T> work(block: T): AttoWork where T : PreviousSupport, T : AttoBlock {
        return work(block.network, block.timestamp, block.previous.value)
    }

    suspend fun work(
        network: AttoNetwork,
        timestamp: Instant,
        target: ByteArray,
    ): AttoWork {
        val threshold = AttoWork.getThreshold(network, timestamp)
        return work(threshold, target)
    }
}
