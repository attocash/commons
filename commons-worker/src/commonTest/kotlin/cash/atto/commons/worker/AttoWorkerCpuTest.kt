package cash.atto.commons.worker

import cash.atto.commons.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class AttoWorkerCpuTest {
    private val hash = AttoHash("0000000000000000000000000000000000000000000000000000000000000000".fromHexToByteArray())

    companion object {
        private val worker = AttoWorker.cpu(1U)
    }

    @Test
    fun `should perform work with cpu`() =
        runTest {
            val network = AttoNetwork.LOCAL
            val timestamp = AttoNetwork.INITIAL_INSTANT
            val work = worker.work(network, timestamp, hash.value)
            assertTrue(AttoWork.isValid(network, timestamp, hash.value, work.value))
        }
}
