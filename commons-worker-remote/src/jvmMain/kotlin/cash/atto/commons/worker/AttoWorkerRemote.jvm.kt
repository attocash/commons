package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.future
import kotlinx.datetime.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

@OptIn(DelicateCoroutinesApi::class)
private class AttoWorkerJavaImpl(
    private val worker: AttoWorker,
    executorService: ExecutorService,
) : AttoWorkerJava {
    private val scope = CoroutineScope(executorService.asCoroutineDispatcher() + SupervisorJob())

    override fun work(
        threshold: String,
        target: ByteArray,
    ): CompletableFuture<AttoWork> =
        scope.future {
            worker.work(threshold.toULong(), target)
        }

    override fun work(
        network: AttoNetwork,
        timestamp: String,
        target: ByteArray,
    ): CompletableFuture<AttoWork> =
        scope.future {
            worker.work(network, Instant.parse(timestamp), target)
        }

    override fun work(block: AttoBlock): CompletableFuture<AttoWork> = scope.future { worker.work(block) }
}

fun createAttoWorkerJava(
    url: String,
    executorService: ExecutorService,
): AttoWorkerJava =
    AttoWorkerJavaImpl(
        AttoWorker.remote(url),
        executorService,
    )

fun createAttoWorkerJavaCached(
    url: String,
    executorService: ExecutorService,
): AttoWorkerJava =
    AttoWorkerJavaImpl(
        AttoWorker.remote(url).cached(),
        executorService,
    )
