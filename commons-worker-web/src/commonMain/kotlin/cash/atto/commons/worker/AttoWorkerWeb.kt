package cash.atto.commons.worker

import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget


expect suspend fun AttoWorker.Companion.isWebgpuSupported(): Boolean

expect fun AttoWorker.Companion.isWebglSupported(): Boolean

suspend fun AttoWorker.Companion.isWebSupported(): Boolean {
    return AttoWorker.isWebgpuSupported() || AttoWorker.isWebglSupported()
}

fun AttoWorker.Companion.webgpu(): AttoWorker = AttoWorkerWebGPU()

fun AttoWorker.Companion.webgl(): AttoWorker = AttoWorkerWebGL()

suspend fun AttoWorker.Companion.web(): AttoWorker {
    if (AttoWorker.isWebgpuSupported()) {
        return AttoWorker.webgpu()
    }
    return AttoWorker.webgl()
}


expect class AttoWorkerWebGPU() : AttoWorker {
    override suspend fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoWork

    override fun close()
}

expect class AttoWorkerWebGL() : AttoWorker {
    override suspend fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoWork

    override fun close()
}
