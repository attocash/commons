package cash.atto.commons.worker

import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.toByteArray
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@Deprecated(
    "Moved to AttoWorker.cpu(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoWorker.cpu(parallelism)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoWorker.Companion.cpu(parallelism: UShort): AttoWorker = AttoWorker.cpu(parallelism)

@Deprecated(
    "Moved to AttoWorker.cpu(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoWorker.cpu()"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoWorker.Companion.cpu(): AttoWorker = AttoWorker.cpu()

internal class AttoWorkerCpu(
    val parallelism: UShort,
) : AttoWorker {
    private val supervisorJob = SupervisorJob()
    private val dispatcher = Dispatchers.Default.limitedParallelism(parallelism.toInt())

    override suspend fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoWork {
        val workJob = Job(supervisorJob)
        val callerCancellation =
            coroutineContext[Job]?.invokeOnCompletion { cause ->
                if (cause != null) {
                    workJob.cancel()
                }
            }
        try {
            val controller = WorkerController(dispatcher + workJob, parallelism, threshold, target.value)
            return controller.calculate()
        } finally {
            callerCancellation?.dispose()
            workJob.cancel()
        }
    }

    override fun close() {
        supervisorJob.cancel()
    }
}

private class WorkerController(
    context: CoroutineContext,
    private val parallelism: UShort,
    private val threshold: ULong,
    private val target: ByteArray,
) {
    private val scope = CoroutineScope(context)

    private val result =
        CompletableDeferred<AttoWork>().apply {
            this.invokeOnCompletion {
                scope.cancel()
            }
        }

    private fun tryComplete(work: ByteArray): Boolean {
        if (!AttoWork.isValid(threshold, target, work)) {
            return false
        }
        return result.complete(AttoWork(work.copyOf()))
    }

    suspend fun calculate(): AttoWork {
        val rangeSize = ULong.MAX_VALUE / parallelism.toULong()

        repeat(parallelism.toInt()) { i ->
            scope.launch {
                val start = i.toULong() * rangeSize
                val end = start + rangeSize
                val work = start.toByteArray()
                var current = start
                var iterationsUntilYield = 4_096
                while (current != end && isActive) {
                    if (tryComplete(work)) return@launch
                    incrementByteArray(work)
                    current++
                    iterationsUntilYield--
                    if (iterationsUntilYield == 0) {
                        iterationsUntilYield = 4_096
                        yield()
                    }
                }
            }
        }
        try {
            return result.await()
        } finally {
            scope.cancel()
        }
    }

    private fun incrementByteArray(byteArray: ByteArray) {
        var carry = 1
        for (i in byteArray.indices) {
            val sum = (byteArray[i].toInt() and 0xFF) + carry
            byteArray[i] = sum.toByte()
            carry = if (sum > 0xFF) 1 else 0
            if (carry == 0) break
        }
    }
}
