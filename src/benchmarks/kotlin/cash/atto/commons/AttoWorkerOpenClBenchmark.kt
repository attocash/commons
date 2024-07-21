package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads
import kotlin.random.Random

@State(Scope.Thread)
open class AttoWorkerOpenClBenchmark {
    private val openclWorker = AttoWorker.opencl(0U)

    @Benchmark
    @Threads(4)
    fun workOpenCl() {
        openclWorker.work(AttoNetwork.LOCAL, INITIAL_INSTANT, Random.Default.nextBytes(ByteArray(32)))
    }
}
