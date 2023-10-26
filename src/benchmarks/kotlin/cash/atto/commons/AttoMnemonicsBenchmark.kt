package cash.atto.commons

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
open class AttoMnemonicsBenchmark {
    private val mnemonic = AttoMnemonic.generate()

    @Benchmark
    fun generate() {
        AttoMnemonic.generate()
    }

    @Benchmark
    fun toEntropy() {
        mnemonic.toEntropy()
    }

}
