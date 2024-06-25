package cash.atto.commons

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
open class AttoSeedBenchmark {
    private val mnemonic = AttoMnemonic.generate()

    @Benchmark
    fun toSeed() {
        mnemonic.toSeed()
    }
}
