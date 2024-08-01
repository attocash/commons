package cash.atto.commons

import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

fun AttoWorker.Companion.cpu(): AttoWorker = cpu(Runtime.getRuntime().availableProcessors().toUShort())

fun AttoWorker.Companion.cpu(count: UShort): AttoWorker = AttoWorkerCpu(count)


private class AttoWorkerCpu(val count: UShort) : AttoWorker, Closeable {
    private val threadPool = Executors.newFixedThreadPool(count.toInt())

    override fun work(
        threshold: ULong,
        target: ByteArray,
    ): AttoWork {
        val controller = WorkerController()
        val rangeSize = ULong.MAX_VALUE / count

        for (i in 0U until count.toUInt()) {
            val worker = Worker(controller, threshold, target, i * rangeSize, rangeSize)
            threadPool.execute {
                worker.work()
            }
        }

        return controller.use {
            it.get()
        }
    }

    override fun close() {
        threadPool.shutdown()
    }
}

private class WorkerController : Closeable {
    @Volatile
    private var work: AttoWork? = null
    private val lock = ReentrantLock()
    private val notEmpty: Condition = lock.newCondition()

    fun isEmpty(): Boolean {
        return work == null
    }

    fun add(work: ByteArray) {
        lock.withLock {
            this.work = AttoWork(work)
            notEmpty.signalAll()
        }
    }

    fun get(): AttoWork {
        lock.withLock {
            while (true) {
                val currentWork = work
                if (currentWork != null) {
                    return currentWork
                }
                notEmpty.await()
            }
        }
    }

    override fun close() {
        lock.withLock {
            if (work == null) {
                work = AttoWork(ByteArray(8))
            }
            notEmpty.signalAll()
        }
    }
}


private class Worker(
    val controller: WorkerController,
    val threshold: ULong,
    val target: ByteArray,
    val startWork: ULong,
    val size: ULong,
) {

    fun work() {
        var tries = 0UL
        val work = startWork.toByteArray()
        while (tries < size) {
            if (isValid(threshold, target, work)) {
                controller.add(work)
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
