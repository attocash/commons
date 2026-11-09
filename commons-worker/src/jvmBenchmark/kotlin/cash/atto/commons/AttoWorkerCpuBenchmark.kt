package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import cash.atto.commons.worker.AttoWorker
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown

@State(Scope.Thread)
open class AttoWorkerCpuBenchmark {
    @Param("1", "4")
    var parallelism: Int = 1

    private lateinit var worker: AttoWorker
    private val targets =
        List(256) { targetIndex ->
            AttoWorkTarget(ByteArray(AttoWorkTarget.SIZE) { byteIndex -> (targetIndex * 37 + byteIndex * 17).toByte() })
        }
    private var nextTarget = 0

    @Setup
    fun setup() {
        worker = AttoWorker.cpu(parallelism.toUShort())
        nextTarget = 0
    }

    @Benchmark
    fun work(): AttoWork =
        runBlocking {
            val target = targets[nextTarget]
            nextTarget = (nextTarget + 1) % targets.size
            worker.work(AttoNetwork.LOCAL, INITIAL_INSTANT, target)
        }

    @TearDown
    fun tearDown() {
        worker.close()
    }
}
