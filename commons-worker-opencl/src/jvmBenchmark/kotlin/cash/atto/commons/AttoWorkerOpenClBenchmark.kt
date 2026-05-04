package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.opencl
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads
import kotlin.time.Duration.Companion.days

const val TARGET_COUNT = 4_096

@State(Scope.Thread)
open class AttoWorkerOpenClBenchmark {
    private val network = AttoNetwork.BETA
    private val worker = AttoWorker.opencl()
    private val targets =
        Array(TARGET_COUNT) {
            AttoWorkTarget(AttoHasher.hash(32, network.name.toByteArray(), it.toULong().toByteArray()))
        }
    private var targetIndex = 0
    private val tickInstant = INITIAL_INSTANT
    private val tockInstant = tickInstant.plus(800.days)

    @Benchmark
    @Threads(4)
    fun tickWorkOpenCl() {
        runBlocking {
            worker.work(network, tickInstant, nextTarget())
        }
    }

    @Benchmark
    @Threads(4)
    fun tockWorkOpenCl() {
        runBlocking {
            worker.work(network, tockInstant, nextTarget())
        }
    }

    private fun nextTarget(): AttoWorkTarget = targets[targetIndex++ % TARGET_COUNT]
}
