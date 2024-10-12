package cash.atto.commons

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
open class AttoKeysBenchmark {
    private val mnemonic = AttoMnemonic.generate()
    private val seed = mnemonic.toSeed()
    private val privateKey = seed.toPrivateKey(0U)

    @Benchmark
    fun toPrivateKey() {
        seed.toPrivateKey(0U)
    }

    @Benchmark
    fun toPublicKey() {
        privateKey.toPublicKey()
    }
}
