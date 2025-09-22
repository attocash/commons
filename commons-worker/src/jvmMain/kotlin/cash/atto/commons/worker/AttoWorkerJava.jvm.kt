package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import java.util.concurrent.CompletableFuture

interface AttoWorkerJava {
    fun work(
        threshold: String,
        target: ByteArray,
    ): CompletableFuture<AttoWork>

    fun work(
        network: AttoNetwork,
        timestamp: String,
        target: ByteArray,
    ): CompletableFuture<AttoWork>

    fun work(block: AttoBlock): CompletableFuture<AttoWork>
}
