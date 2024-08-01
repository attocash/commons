package cash.atto.commons

import kotlinx.datetime.Instant
import java.io.Closeable

sealed interface AttoWorker : Closeable {
    companion object

    fun work(
        threshold: ULong,
        target: ByteArray,
    ): AttoWork

    fun work(block: AttoOpenBlock): AttoWork {
        return work(block.network, block.timestamp, block.publicKey.value)
    }

    fun <T> work(block: T): AttoWork where T : PreviousSupport, T : AttoBlock {
        return work(block.network, block.timestamp, block.previous.value)
    }

    fun work(
        network: AttoNetwork,
        timestamp: Instant,
        target: ByteArray,
    ): AttoWork {
        val threshold = getThreshold(network, timestamp)
        return work(threshold, target)
    }
}
