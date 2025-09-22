package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.getTarget
import cash.atto.commons.isValid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap

private class AttoWorkerCache(
    private val delegate: AttoWorker,
) : AttoWorker {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val cache = ConcurrentHashMap<Target, Deferred<AttoWork>>()

    private fun delegateWork(
        target: Target,
        delegate: suspend () -> AttoWork,
    ): Deferred<AttoWork> {
        return cache.computeIfAbsent(target) {
            scope.async {
                try {
                    return@async delegate.invoke()
                } catch (e: Exception) {
                    cache.remove(target)
                    throw e
                }
            }
        }
    }

    override suspend fun work(
        threshold: ULong,
        target: ByteArray,
    ): AttoWork {
        val deferred =
            delegateWork(Target(target)) {
                delegate.work(threshold, target)
            }

        val work = deferred.await()

        cache.remove(Target(target))

        if (AttoWork.isValid(threshold, target, work.value)) {
            return work
        }

        return work(threshold, target)
    }

    override suspend fun work(
        network: AttoNetwork,
        timestamp: Instant,
        target: ByteArray,
    ): AttoWork {
        val deferred =
            delegateWork(Target(target)) {
                delegate.work(network, timestamp, target)
            }

        val work = deferred.await()

        cache.remove(Target(target))

        if (AttoWork.isValid(network, timestamp, target, work.value)) {
            return work
        }

        return work(network, timestamp, target)
    }

    override suspend fun work(block: AttoBlock): AttoWork {
        val deferred =
            delegateWork(Target(block.getTarget())) {
                delegate.work(block)
            }

        val work = deferred.await()

        cache.remove(Target(block.getTarget()))

        delegateWork(Target(block.getTarget())) {
            delegate.work(block.network, Clock.System.now(), block.hash.value)
        }

        if (work.isValid(block)) {
            return work
        }

        return work(block)
    }

    override fun close() {
        cache.clear()
        scope.cancel()
        delegate.close()
    }

    private data class Target(
        val target: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Target

            if (!target.contentEquals(other.target)) return false

            return true
        }

        override fun hashCode(): Int = target.contentHashCode()
    }
}

fun AttoWorker.cached(): AttoWorker = AttoWorkerCache(this)
