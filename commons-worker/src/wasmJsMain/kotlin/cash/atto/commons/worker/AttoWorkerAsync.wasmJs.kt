package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.js.JsName

@JsExportForJs
actual class AttoWorkerAsync internal actual constructor(
    actual val worker: AttoWorker,
    dispatcher: CoroutineDispatcher,
) {
    @JsName("workThreshold")
    suspend fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoWork = worker.work(threshold, target)

    @JsName("workNetwork")
    suspend fun work(
        network: AttoNetwork,
        timestamp: AttoInstant,
        target: AttoWorkTarget,
    ): AttoWork = worker.work(network, timestamp, target)

    @JsName("workBlock")
    suspend fun work(block: AttoBlock): AttoWork = worker.work(block)
}

fun AttoWorker.toAsync(): AttoWorkerAsync = AttoWorkerAsync(this, Dispatchers.Default)
