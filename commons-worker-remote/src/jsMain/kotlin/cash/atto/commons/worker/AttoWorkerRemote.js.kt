package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.Promise

@OptIn(DelicateCoroutinesApi::class)
private class AttoWorkerJsPromiseImpl(
    private val worker: AttoWorker,
) : AttoWorkerJs {
    override fun workThreshold(
        threshold: String,
        target: ByteArray,
    ): Promise<AttoWork> =
        GlobalScope.promise {
            worker.work(threshold.toULong(), target)
        }

    override fun workNetwork(
        network: AttoNetwork,
        timestamp: String,
        target: ByteArray,
    ): Promise<AttoWork> =
        GlobalScope.promise {
            worker.work(network, AttoInstant.fromIso(timestamp), target)
        }

    override fun workBlock(block: AttoBlock): Promise<AttoWork> = GlobalScope.promise { worker.work(block) }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun createAttoWorker(url: String): AttoWorkerJs =
    AttoWorkerJsPromiseImpl(
        AttoWorker.remote(url),
    )
