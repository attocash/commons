package cash.atto.commons.worker

import cash.atto.commons.AttoHash
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.fromHexToByteArray
import cash.atto.commons.isValid
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class AttoWorkerCpuTest {
    private val hash = AttoHash("0000000000000000000000000000000000000000000000000000000000000000".fromHexToByteArray())

    companion object {
        private val worker = AttoWorker.cpu()
    }

    @Test
    fun `should perform work with cpu`() =
        runTest {
            val network = AttoNetwork.LOCAL
            val timestamp = AttoNetwork.INITIAL_INSTANT
            val target = AttoWorkTarget(hash.value)
            val work = worker.work(network, timestamp, target)
            assertTrue(AttoWork.isValid(network, timestamp, target, work.value))
        }
}
