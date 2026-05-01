package cash.atto.commons.worker

import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget

fun AttoWorker.Companion.webgpu(): AttoWorker = AttoWorkerWebGPU()

expect val AttoWorker.Companion.isWebgpuSupported: Boolean

expect class AttoWorkerWebGPU() : AttoWorker {
    override suspend fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoWork

    override fun close()
}
