package cash.atto.commons.worker

fun AttoWorker.Companion.webgpu(): AttoWorker = AttoWorkerWebGPU()

expect val AttoWorker.Companion.isWebgpuSupported: Boolean

expect class AttoWorkerWebGPU() : AttoWorker
