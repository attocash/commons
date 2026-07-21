package cash.atto.commons.worker

import cash.atto.commons.AttoHash
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.fromHexToByteArray
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class AttoWorkerWebGLTest {
    private val hash = AttoHash("0000000000000000000000000000000000000000000000000000000000000000".fromHexToByteArray())

    @Test
    fun `should perform work with webgl`() =
        runTest {
            if (!AttoWorker.isWebglSupported()) {
                println("Skipping WebGL worker test because this JavaScript runtime does not expose the required WebGL2 API.")
                return@runTest
            }

            val worker = AttoWorker.webgl()
            try {
                val network = AttoNetwork.LOCAL
                val timestamp = AttoNetwork.INITIAL_INSTANT
                val target = AttoWorkTarget(hash.value)
                val work = worker.work(network, timestamp, target)
                assertTrue(AttoWork.isValid(network, timestamp, target, work.value))
            } finally {
                worker.close()
            }
        }
}
