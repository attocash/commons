package cash.atto.commons.signer

import cash.atto.commons.AttoWork
import cash.atto.commons.isValid
import cash.atto.commons.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun AttoWorker.Companion.cpu(count: UShort): AttoWorker = AttoWorkerCpu(count)

internal class AttoWorkerCpu(
    val count: UShort,
) : AttoWorker {
    private val dispatcher = Dispatchers.Default.limitedParallelism(count.toInt())

    override suspend fun work(
        threshold: ULong,
        target: ByteArray,
    ): AttoWork = coroutineScope {
        val controller = WorkerController()
        val rangeSize = ULong.MAX_VALUE / count

        val jobs = (0U until count.toUInt()).map { i ->
            launch(dispatcher) {
                val worker = Worker(
                    controller,
                    threshold,
                    target,
                    i * rangeSize,
                    rangeSize
                )
                worker.work()
            }
        }

        jobs.joinAll()

        controller.get()
    }

    override fun close() {
        dispatcher.cancel()
    }
}

private class WorkerController {
    @Volatile
    private var work: AttoWork? = null
    private val mutex = Mutex()

    suspend fun isEmpty(): Boolean = mutex.withLock {
        work == null
    }

    suspend fun add(work: ByteArray) {
        mutex.withLock {
            if (this.work == null) {
                this.work = AttoWork(work)
            }
        }
    }

    suspend fun get(): AttoWork = mutex.withLock {
        while (work == null) {
            mutex.unlock()
            delay(10)
            mutex.lock()
        }
        return work!!
    }
}

private class Worker(
    val controller: WorkerController,
    val threshold: ULong,
    val target: ByteArray,
    val startWork: ULong,
    val size: ULong,
) {
    suspend fun work() {
        var tries = 0UL
        val work = startWork.toByteArray()
        while (tries < size) {
            if (AttoWork.isValid(threshold, target, work)) {
                controller.add(work)
                return
            }
            if (!controller.isEmpty()) {
                return
            }

            incrementByteArray(work)

            tries++
        }
    }

    fun incrementByteArray(byteArray: ByteArray) {
        var carry: Byte = 1
        for (i in byteArray.indices) {
            val oldByte = byteArray[i]
            val newByte = (oldByte + carry).toByte()
            byteArray[i] = newByte
            carry = if (newByte < oldByte) 1 else 0
            if (carry == 0.toByte()) break
        }
    }
}
