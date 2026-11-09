package cash.atto.commons.worker

import cash.atto.commons.AttoHash
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.fromHexToByteArray
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
            // Given
            val target = AttoWorkTarget(hash.value)
            val worker = AttoWorker.cpu()

            try {
                // When
                assertFailsWith<TimeoutCancellationException> {
                    withTimeout(10.milliseconds) {
                        worker.work(0UL, target)
                    }
                }
                val work = worker.work(ULong.MAX_VALUE, target)

                // Then
                assertTrue(AttoWork.isValid(ULong.MAX_VALUE, target, work.value))
            } finally {
                worker.close()
            }
        }

    @Test
    fun `should isolate simultaneous requests on the same cpu worker`() =
        runTest {
            // Given
            val worker = AttoWorker.cpu(2U)
            val targets = List(8) { index -> AttoWorkTarget(ByteArray(AttoWorkTarget.SIZE) { (it + index * 31).toByte() }) }
            val threshold = 0x0FFF_FFFF_FFFF_FFFFUL

            try {
                // When
                val works = targets.map { target -> async { worker.work(threshold, target) } }.awaitAll()

                // Then
                for ((target, work) in targets.zip(works)) {
                    assertTrue(AttoWork.isValid(threshold, target, work.value))
                }
            } finally {
                worker.close()
            }
        }
}
