package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.getTarget
import cash.atto.commons.isValid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private class AttoWorkerCache(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val delegate: AttoWorker,
) : AttoWorker {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val mutex = Mutex()
    private val cache = HashMap<AttoWorkTarget, Deferred<AttoWork>>()

    private suspend fun delegateWork(
        target: AttoWorkTarget,
        delegate: suspend (AttoWorkTarget) -> AttoWork,
    ): Deferred<AttoWork> {
        mutex.withLock {
            var deferred = cache[target]
            if (deferred != null) {
                return deferred
            }
            deferred =
                scope.async {
                    try {
                        return@async delegate.invoke(target)
                    } catch (e: Exception) {
                        mutex.withLock {
                            cache.remove(target)
                        }
                        throw e
                    }
                }

            cache[target] = deferred

            return deferred
        }
    }

    override suspend fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoWork {
        val deferred =
            delegateWork(target) {
                delegate.work(threshold, it)
            }

        val work = deferred.await()

        mutex.withLock {
            cache.remove(target)
        }

        if (AttoWork.isValid(threshold, target, work.value)) {
            return work
        }

        return work(threshold, target)
    }

    override suspend fun work(
        network: AttoNetwork,
        timestamp: AttoInstant,
        target: AttoWorkTarget,
    ): AttoWork {
        val deferred =
            delegateWork(target) {
                delegate.work(network, timestamp, it)
            }

        val work = deferred.await()

        mutex.withLock {
            cache.remove(target)
        }

        if (AttoWork.isValid(network, timestamp, target, work.value)) {
            return work
        }

        return work(network, timestamp, target)
    }

    override suspend fun work(block: AttoBlock): AttoWork {
        val deferred =
            delegateWork(block.getTarget()) {
                delegate.work(block)
            }

        val work = deferred.await()

        mutex.withLock {
            cache.remove(block.getTarget())
        }

        val nextTarget = AttoWorkTarget(block.hash.value)
        delegateWork(nextTarget) {
            delegate.work(block.network, AttoInstant.now(), it)
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
}

fun AttoWorker.cached(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoWorker = AttoWorkerCache(dispatcher, this)
