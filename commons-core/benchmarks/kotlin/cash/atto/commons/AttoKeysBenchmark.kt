package cash.atto.commons

import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
open class AttoKeysBenchmark {
    private val mnemonic = runBlocking { AttoMnemonic.generate() }
    private val seed = runBlocking { mnemonic.toSeed() }
    private val privateKey = runBlocking { seed.toPrivateKey(0U) }

    @Benchmark
    fun toPrivateKey() {
        runBlocking { seed.toPrivateKey(0U) }
    }

    @Benchmark
    fun toPublicKey() {
        runBlocking { privateKey.toPublicKey() }
    }
}
