package cash.atto.commons.worker

import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.isValid
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AttoWorkerOperationsTest {
    @Test
    fun `should reject invalid operation response work`() =
        runTest {
            val target = AttoWorkTarget(Random.nextBytes(32))
            val timestamp = AttoInstant.now()
            val invalidWork =
                generateSequence { AttoWork(Random.nextBytes(8)) }
                    .first { !AttoWork.isValid(AttoNetwork.LOCAL, timestamp, target, it.value) }

            val worker =
                object : AttoWorkerOperations {
                    override suspend fun work(
                        threshold: ULong,
                        target: AttoWorkTarget,
                    ): AttoWork = invalidWork

                    override suspend fun work(request: AttoWorkerOperations.Request): AttoWorkerOperations.Response =
                        AttoWorkerOperations.Response(invalidWork)

                    override fun close() {
                    }
                }

            assertFailsWith<IllegalArgumentException> {
                worker.work(AttoNetwork.LOCAL, timestamp, target)
            }
        }
}
