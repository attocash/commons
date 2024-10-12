package cash.atto.commons.work

fun AttoWorker.Companion.cpu(): AttoWorker = cpu(Runtime.getRuntime().availableProcessors().toUShort())
