package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import kotlin.random.Random

@State(Scope.Thread)
open class AttoWorkerCpuBenchmark {
    private val openclWorker = AttoWorker.cpu()

    @Benchmark
    fun workOpenCl() {
        openclWorker.work(AttoNetwork.LOCAL, INITIAL_INSTANT, Random.Default.nextBytes(ByteArray(32)))
    }
}
