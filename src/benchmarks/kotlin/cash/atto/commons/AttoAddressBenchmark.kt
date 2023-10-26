package cash.atto.commons

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
open class AttoAddressBenchmark {
    private val privateKey = AttoPrivateKey.generate()
    private val publicKey = privateKey.toPublicKey()
    private val address = publicKey.toAddress()

    @Benchmark
    fun toAddress() {
        publicKey.toAddress()
    }

    @Benchmark
    fun isValid() {
        AttoAddress.isValid(address.value)
    }
}
