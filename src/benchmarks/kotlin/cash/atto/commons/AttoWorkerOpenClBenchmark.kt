package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads
import kotlin.random.Random

@State(Scope.Thread)
open class AttoWorkerOpenClBenchmark {
    private val worker = AttoWorker.opencl(0U)
    private val byteArray = ByteArray(32)

    @Benchmark
    @Threads(4)
    fun workOpenCl() {
        worker.work(AttoNetwork.LOCAL, INITIAL_INSTANT, Random.Default.nextBytes(byteArray))
    }
}
