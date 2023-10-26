package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import kotlin.random.Random

@State(Scope.Thread)
open class AttoWorkBenchmark {
    private val network = AttoNetwork.LOCAL
    private val timestamp = INITIAL_INSTANT
    private val hash = AttoHash(Random.nextBytes(ByteArray(32)))
    private val work = AttoWork.work(network, timestamp, hash)

    @Benchmark
    fun work() {
        AttoWork.work(network, timestamp, hash)
    }

    @Benchmark
    fun isValid() {
        AttoWork.isValid(network, timestamp, hash, work)
    }

}
