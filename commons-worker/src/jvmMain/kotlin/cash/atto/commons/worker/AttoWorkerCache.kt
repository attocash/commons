package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoWork
import cash.atto.commons.getThreshold
import cash.atto.commons.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

private class AttoWorkerCache(
    private val delegate: AttoWorker,
) : AttoWorker {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val cache = ConcurrentHashMap<Pair<ULong, String>, AttoWork>()

    override suspend fun work(
        threshold: ULong,
        target: ByteArray,
    ): AttoWork {
        val work = cache.remove(threshold to target.toHex())
        if (work != null) {
            return work
        }
        return delegate.work(threshold, target)
    }

    override suspend fun work(block: AttoBlock): AttoWork {
        val work = super.work(block)
        scope.launch {
            try {
                val nextThreshold = AttoWork.getThreshold(block.network, Clock.System.now())
                val nextTarget = block.hash.value
                val key = nextThreshold to nextTarget.toHex()
                if (!cache.containsKey(key)) {
                    val preComputed = delegate.work(nextThreshold, nextTarget)
                    cache.putIfAbsent(key, preComputed)
                }
            } catch (_: Throwable) {
                println("Failed to cache pre computed work for $block")
            }
        }
        return work
    }

    override fun close() {
        cache.clear()
        scope.cancel()
        delegate.close()
    }
}

fun AttoWorker.cached(): AttoWorker = AttoWorkerCache(this)
