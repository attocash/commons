package cash.atto.commons

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import kotlin.random.Random

@State(Scope.Thread)
open class AttoSignatureBenchmark {
    private val privateKey = AttoPrivateKey.generate()
    private val publicKey = privateKey.toPublicKey()
    private val hash = AttoHash(Random.nextBytes(ByteArray(32)))
    private val signature = AttoSignature.sign(privateKey, hash)

    @Benchmark
    fun sign() {
        AttoSignature.sign(privateKey, hash)
    }

    @Benchmark
    fun isValid() {
        signature.isValid(publicKey, hash)
    }
}
