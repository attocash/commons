package cash.atto.commons.worker

fun AttoWorker.Companion.cpu(): AttoWorker = cpu(Runtime.getRuntime().availableProcessors().toUShort())
