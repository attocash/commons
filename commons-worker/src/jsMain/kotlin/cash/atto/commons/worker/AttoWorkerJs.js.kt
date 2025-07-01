package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import kotlin.js.Promise

@OptIn(ExperimentalJsExport::class)
@JsExport
interface AttoWorkerJs {
    fun workThreshold(
        threshold: String,
        target: ByteArray,
    ): Promise<AttoWork>

    fun workNetwork(
        network: AttoNetwork,
        timestamp: String,
        target: ByteArray,
    ): Promise<AttoWork>

    fun workBlock(block: AttoBlock): Promise<AttoWork>
}
