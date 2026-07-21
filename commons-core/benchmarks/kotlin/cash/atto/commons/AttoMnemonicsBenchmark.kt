package cash.atto.commons

import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
open class AttoMnemonicsBenchmark {
    private val mnemonic = runBlocking { AttoMnemonic.generate() }

    @Benchmark
    fun generate() {
        runBlocking { AttoMnemonic.generate() }
    }

    @Benchmark
    fun toEntropy() {
        mnemonic.toEntropy()
    }
}
