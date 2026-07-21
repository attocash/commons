package cash.atto.commons.worker

internal actual fun defaultCpuWorker(): AttoWorker = AttoWorker.cpu(Runtime.getRuntime().availableProcessors().toUShort())
