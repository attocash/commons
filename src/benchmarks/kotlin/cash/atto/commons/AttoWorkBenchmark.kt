package cash.atto.commons

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import kotlin.random.Random

@State(Scope.Thread)
open class AttoWorkBenchmark {
    val openBlock =
        AttoOpenBlock(
            version = 0U.toAttoVersion(),
            network = AttoNetwork.LOCAL,
            algorithm = AttoAlgorithm.V1,
            publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
            balance = AttoAmount.MAX,
            timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
            sendHashAlgorithm = AttoAlgorithm.V1,
            sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32))),
            representative = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
        )
    private val work = AttoWorker.cpu().work(openBlock)

    @Benchmark
    fun isValid() {
        work.isValid(openBlock)
    }
}
