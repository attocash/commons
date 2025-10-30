package cash.atto.commons.worker

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration

class AttoWorkerRetry(
    private val worker: AttoWorker,
    private val every: Duration,
) : AttoWorker {
    private val logger = KotlinLogging.logger {}

    private suspend fun <T> retry(worker: suspend () -> T): T =
        coroutineScope {
            while (isActive) {
                try {
                    return@coroutineScope worker()
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    logger.warn(t) { "Error while calculating work. Retrying in $every..." }
                    delay(every)
                }
            }
            throw CancellationException("Cancelled while retrying")
        }

    override suspend fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoWork = retry { worker.work(threshold, target) }

    override suspend fun work(
        network: AttoNetwork,
        timestamp: AttoInstant,
        target: AttoWorkTarget,
    ): AttoWork = retry { worker.work(network, timestamp, target) }

    override suspend fun work(block: AttoBlock): AttoWork = retry { worker.work(block) }

    override fun close() {
        worker.close()
    }
}

fun AttoWorker.retry(every: Duration): AttoWorker = AttoWorkerRetry(this, every)
