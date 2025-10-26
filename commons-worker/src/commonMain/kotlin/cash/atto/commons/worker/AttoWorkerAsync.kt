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
import kotlin.js.JsName

@JsExportForJs
class AttoWorkerAsync(
    private val worker: AttoWorker,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    @JsName("workThreshold")
    fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoFuture<AttoWork> = scope.submit { worker.work(threshold, target) }

    @JsName("workNetwork")
    fun work(
        network: AttoNetwork,
        timestamp: AttoInstant,
        target: AttoWorkTarget,
    ): AttoFuture<AttoWork> = scope.submit { worker.work(network, timestamp, target) }

    @JsName("workBlock")
    fun work(block: AttoBlock): AttoFuture<AttoWork> = scope.submit { worker.work(block) }
}

fun AttoWorker.toAsync(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoWorkerAsync = AttoWorkerAsync(this, dispatcher)
