package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

@State(Scope.Thread)
open class AttoWorkerOpenClBenchmark {
    private val worker = AttoWorker.opencl(0U)
    private val byteArray = ByteArray(32)
    private val tickInstant = INITIAL_INSTANT
    private val tockInstant = tickInstant.plus(800.days)

    @Benchmark
    @Threads(4)
    fun tickWorkOpenCl() {
        worker.work(AttoNetwork.LOCAL, INITIAL_INSTANT, Random.Default.nextBytes(byteArray))
    }

    @Benchmark
    @Threads(4)
    fun tockWorkOpenCl() {
        worker.work(AttoNetwork.LOCAL, tockInstant, Random.Default.nextBytes(byteArray))
    }
}
