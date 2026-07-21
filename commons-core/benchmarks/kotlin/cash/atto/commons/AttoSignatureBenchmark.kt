package cash.atto.commons

import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import kotlin.random.Random

@State(Scope.Thread)
open class AttoSignatureBenchmark {
    private val privateKey = AttoPrivateKey.generate()
    private val publicKey = runBlocking { privateKey.toPublicKey() }
    private val hash = AttoHash(Random.nextBytes(ByteArray(32)))
    private val signature = runBlocking { privateKey.sign(hash) }

    @Benchmark
    fun sign() {
        runBlocking { privateKey.sign(hash) }
    }

    @Benchmark
    fun isValid() {
        runBlocking { signature.isValid(publicKey, hash) }
    }
}
