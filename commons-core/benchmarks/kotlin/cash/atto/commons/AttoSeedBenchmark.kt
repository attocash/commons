package cash.atto.commons

import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
open class AttoSeedBenchmark {
    private val mnemonic = runBlocking { AttoMnemonic.generate() }

    @Benchmark
    fun toSeed() {
        runBlocking { mnemonic.toSeed() }
    }
}
