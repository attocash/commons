package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.opencl
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

@State(Scope.Thread)
open class AttoWorkerOpenClBenchmark {
    private val worker = AttoWorker.opencl()
    private val byteArray = ByteArray(32)
    private val tickInstant = INITIAL_INSTANT
    private val tockInstant = tickInstant.plus(800.days)

    @Benchmark
    @Threads(4)
    fun tickWorkOpenCl() {
        runBlocking {
            worker.work(AttoNetwork.LIVE, INITIAL_INSTANT, AttoWorkTarget(Random.nextBytes(byteArray)))
        }
    }

    @Benchmark
    @Threads(4)
    fun tockWorkOpenCl() {
        runBlocking {
            worker.work(AttoNetwork.LIVE, tockInstant, AttoWorkTarget(Random.nextBytes(byteArray)))
        }
    }
}
