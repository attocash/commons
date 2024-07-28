package cash.atto.commons

import java.util.stream.Stream
import kotlin.random.Random

private val CPU = AttoWorkerCpu()

fun AttoWorker.Companion.cpu(): AttoWorker = CPU

private class AttoWorkerCpu : AttoWorker {
    override fun work(
        threshold: ULong,
        target: ByteArray,
    ): AttoWork {
        val controller = WorkerController()
        return Stream
            .generate { Worker(controller, threshold, target) }
            .takeWhile { controller.isEmpty() }
            .parallel()
            .peek { it.work() }
            .map { controller.get() }
            .filter { it != null }
            .findAny()
            .get()
    }
}

private class WorkerController {
    @Volatile
    private var work: AttoWork? = null

    fun isEmpty(): Boolean {
        return work == null
    }

    fun add(work: ByteArray) {
        this.work = AttoWork(work)
    }

    fun get(): AttoWork? {
        return work
    }
}

private class Worker(
    val controller: WorkerController,
    val threshold: ULong,
    val hash: ByteArray,
) {
    private val work = ByteArray(8)

    fun work() {
        while (true) {
            Random.nextBytes(work)
            for (i in work.indices) {
                val byte = work[i]
                for (b in -128..126) {
                    work[i] = b.toByte()
                    if (isValid(threshold, hash, work)) {
                        controller.add(work)
                    }
                    if (!controller.isEmpty()) {
                        return
                    }
                }
                work[i] = byte
            }
        }
    }
}
