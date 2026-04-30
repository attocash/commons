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

class AttoWorkerWebGPUTest {
    private val hash = AttoHash("0000000000000000000000000000000000000000000000000000000000000000".fromHexToByteArray())

    @Test
    fun `should perform work with webgpu`() =
        runTest {
            if (!AttoWorker.isWebgpuSupported) {
                println("Skipping WebGPU worker test because this JavaScript runtime does not expose the WebGPU API.")
                return@runTest
            }

            val worker = AttoWorker.webgpu()
            try {
                val network = AttoNetwork.LOCAL
                val timestamp = AttoNetwork.INITIAL_INSTANT
                val target = AttoWorkTarget(hash.value)
                val work =
                    try {
                        worker.work(network, timestamp, target)
                    } catch (exception: IllegalStateException) {
                        if (exception.message == "No WebGPU adapter is available.") {
                            println("Skipping WebGPU worker test because this JavaScript runtime does not expose a WebGPU adapter.")
                            return@runTest
                        }
                        throw exception
                    }
                assertTrue(AttoWork.isValid(network, timestamp, target, work.value))
            } finally {
                worker.close()
            }
        }
}
