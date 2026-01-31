package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

@JsExportForJs
actual class AttoWorkerAsync internal actual constructor(
    actual val worker: AttoWorker,
    dispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): CompletableFuture<AttoWork> = scope.future { worker.work(threshold, target) }

    fun work(
        network: AttoNetwork,
        timestamp: AttoInstant,
        target: AttoWorkTarget,
    ): CompletableFuture<AttoWork> = scope.future { worker.work(network, timestamp, target) }

    fun work(block: AttoBlock): CompletableFuture<AttoWork> = scope.future { worker.work(block) }
}

fun AttoWorker.toAsync(): AttoWorkerAsync = AttoWorkerAsync(this, Dispatchers.Default)
