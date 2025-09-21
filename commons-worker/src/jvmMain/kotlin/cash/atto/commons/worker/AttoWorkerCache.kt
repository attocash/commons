package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoWork
import cash.atto.commons.getThreshold
import cash.atto.commons.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

private class AttoWorkerCache(
    private val delegate: AttoWorker,
) : AttoWorker {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val cache = ConcurrentHashMap<Pair<ULong, String>, Deferred<AttoWork>>()

    private fun delegateWork(
        threshold: ULong,
        target: ByteArray,
    ): Deferred<AttoWork> {
        val key = threshold to target.toHex()

        return cache.computeIfAbsent(key) {
            scope.async {
                try {
                    return@async delegate.work(threshold, target)
                } catch (e: Exception) {
                    cache.remove(threshold to target.toHex())
                    throw e
                }
            }
        }
    }

    override suspend fun work(
        threshold: ULong,
        target: ByteArray,
    ): AttoWork {
        val key = threshold to target.toHex()

        val deferred = delegateWork(threshold, target)

        return try {
            deferred.await()
        } finally {
            cache.remove(key, deferred)
        }
    }

    override suspend fun work(block: AttoBlock): AttoWork {
        val work = super.work(block)

        val nextThreshold = AttoWork.getThreshold(block.network, Clock.System.now())
        val nextTarget = block.hash.value

        delegateWork(nextThreshold, nextTarget)

        return work
    }

    override fun close() {
        cache.clear()
        scope.cancel()
        delegate.close()
    }
}

fun AttoWorker.cached(): AttoWorker = AttoWorkerCache(this)
