package cash.atto.commons.worker

actual fun AttoWorker.Companion.cpu(): AttoWorker = cpu(Runtime.getRuntime().availableProcessors().toUShort())
