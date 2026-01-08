package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.cpu
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import kotlin.random.Random

@State(Scope.Thread)
open class AttoWorkerCpuBenchmark {
    private val worker = AttoWorker.cpu()
    private val byteArray = ByteArray(32)

    @Benchmark
    fun work() {
        runBlocking {
            worker.work(AttoNetwork.LOCAL, INITIAL_INSTANT, AttoWorkTarget(Random.nextBytes(byteArray)))
        }
    }
}
