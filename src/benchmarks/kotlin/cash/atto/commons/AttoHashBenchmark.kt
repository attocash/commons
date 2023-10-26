package cash.atto.commons

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import kotlin.random.Random

@State(Scope.Thread)
open class AttoHashBenchmark {
    private val block = Random.nextBytes(ByteArray(200))


    @Benchmark
    fun hash() {
        AttoHash.hash(32, block)
    }

}
