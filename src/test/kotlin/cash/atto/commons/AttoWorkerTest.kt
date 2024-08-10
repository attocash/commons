package cash.atto.commons

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream

class AttoWorkerTest {
    private val hash = AttoHash("0000000000000000000000000000000000000000000000000000000000000000".fromHexToByteArray())

    @Test
    fun `should perform work with cpu`() {
        work(AttoWorker.cpu())
    }

    @Test
    @Disabled("OpenCL stopped working in Github actions")
    fun `should perform work with opencl`() {
        work(AttoWorker.opencl())
    }

    private fun work(worker: AttoWorker) {
        val network = AttoNetwork.LOCAL
        val timestamp = AttoNetwork.INITIAL_INSTANT
        val work = worker.work(network, timestamp, hash.value)
        assertTrue(isValid(network, timestamp, hash.value, work.value))
    }

    companion object {
        @JvmStatic
        fun workerProvider(): Stream<Arguments> =
            Stream.of(
                Arguments.of(AttoWorker.cpu()),
                Arguments.of(AttoWorker.opencl()),
            )
    }
}
