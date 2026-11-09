package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
private class AttoWorkerCache(
    private val delegate: AttoWorker,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AttoWorker {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val mutex = Mutex()

    // Synchronous close cannot await the mutex: detach the map atomically, keeping all map access guarded.
    private val cache = AtomicReference<MutableMap<AttoWorkTarget, Deferred<AttoWork>>?>(HashMap())

    private suspend fun delegateWork(
        target: AttoWorkTarget,
        delegate: suspend (AttoWorkTarget) -> AttoWork,
    ): Deferred<AttoWork> {
        mutex.withLock {
            val entries = cache.load() ?: return CompletableDeferred<AttoWork>().apply { cancel() }
            var deferred = entries[target]
            if (deferred != null) {
                return deferred
            }
            deferred =
                scope.async {
                    try {
                        return@async delegate.invoke(target)
                    } catch (e: Exception) {
                        mutex.withLock {
                            entries.remove(target)
                        }
                        throw e
                    }
                }

            entries[target] = deferred

            return deferred
        }
    }

    override suspend fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoWork {
        val work =
            consumeWork(target) {
                delegate.work(threshold, it)
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
        val work =
            consumeWork(target) {
                delegate.work(network, timestamp, it)
            }

        if (AttoWork.isValid(network, timestamp, target, work.value)) {
            return work
        }

        return work(network, timestamp, target)
    }

    override suspend fun work(block: AttoBlock): AttoWork {
        val work =
            consumeWork(block.getTarget()) {
                delegate.work(block)
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

    private suspend fun consumeWork(
        target: AttoWorkTarget,
        request: suspend (AttoWorkTarget) -> AttoWork,
    ): AttoWork {
        val deferred = delegateWork(target, request)
        val work = deferred.await()
        mutex.withLock {
            val entries = cache.load()
            if (entries != null && entries[target] === deferred) {
                entries.remove(target)
            }
        }
        return work
    }

    override fun close() {
        cache.store(null)
        scope.cancel()
        delegate.close()
    }
}

fun AttoWorker.cached(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoWorker = AttoWorkerCache(this, dispatcher)
