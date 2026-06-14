package cash.atto.commons.worker

import cash.atto.commons.AttoHash
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.fromHexToByteArray
import cash.atto.commons.isValid
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

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

    @Test
    fun `should cancel in flight cpu work on caller timeout`() =
        runTest {
            val target = AttoWorkTarget(hash.value)
            val worker = AttoWorker.cpu()

            assertFailsWith<TimeoutCancellationException> {
                withTimeout(10.milliseconds) {
                    worker.work(0UL, target)
                }
            }
            worker.close()
        }
}
