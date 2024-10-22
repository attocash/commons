package cash.atto.commons.signer

fun AttoWorker.Companion.cpu(): AttoWorker = cpu(Runtime.getRuntime().availableProcessors().toUShort())
