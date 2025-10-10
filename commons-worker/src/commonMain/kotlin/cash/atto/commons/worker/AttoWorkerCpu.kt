package cash.atto.commons.worker

import cash.atto.commons.AttoWork
import cash.atto.commons.isValid
import cash.atto.commons.toByteArray
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

fun AttoWorker.Companion.cpu(parallelism: UShort): AttoWorker = AttoWorkerCpu(parallelism)

expect fun AttoWorker.Companion.cpu(): AttoWorker

internal class AttoWorkerCpu(
    val parallelism: UShort,
) : AttoWorker {
    private val supervisorJob = SupervisorJob()
    private val dispatcher = Dispatchers.Default.limitedParallelism(parallelism.toInt())

    override suspend fun work(
        threshold: ULong,
        target: ByteArray,
    ): AttoWork {
        val controller = WorkerController(dispatcher + supervisorJob + Job(), parallelism, threshold, target)
        return controller.calculate()
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
                while (current != end && isActive) {
                    if (tryComplete(work)) return@launch
                    incrementByteArray(work)
                    current++
                }
            }
        }
        return result.await()
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
