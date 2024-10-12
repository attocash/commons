package cash.atto.commons.work

import cash.atto.commons.AttoHash
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.fromHexToByteArray
import cash.atto.commons.isValid
import kotlinx.coroutines.runBlocking
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class AttoWorkerTest {
    private val hash = AttoHash("0000000000000000000000000000000000000000000000000000000000000000".fromHexToByteArray())

    companion object {
        private val worker = AttoWorker.opencl(1U)
    }

    @Ignore("OpenCL stopped working in Github actions")
    @Test
    fun `should perform work with opencl`() = runBlocking {
        val network = AttoNetwork.LOCAL
        val timestamp = AttoNetwork.INITIAL_INSTANT
        val work = worker.work(network, timestamp, hash.value)
        assertTrue(AttoWork.isValid(network, timestamp, hash.value, work.value))
    }
}
