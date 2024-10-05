package cash.atto.commons

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class AttoWorkerTest {
    private val hash = AttoHash("0000000000000000000000000000000000000000000000000000000000000000".fromHexToByteArray())

    @Test
    fun `should perform work with cpu`() {
        work(AttoWorker.cpu())
    }

    @Disabled("OpenCL stopped working in Github actions")
    @Test
    fun `should perform work with opencl`() {
        work(AttoWorker.opencl())
    }

    private fun work(worker: AttoWorker) {
        val network = AttoNetwork.LOCAL
        val timestamp = AttoNetwork.INITIAL_INSTANT
        val work = worker.work(network, timestamp, hash.value)
        assertTrue(isValid(network, timestamp, hash.value, work.value))
    }
}
