package cash.atto.commons.worker

import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AttoWorkerEvaluationTest {
    companion object {
        private val worker = AttoWorker.cpu()
    }

    @Test
    fun `should evaluate LIVE network worker`() =
        runTest {
            withContext(Dispatchers.Default) {
                // when
                val evaluations = worker.evaluate(AttoNetwork.LIVE, 2.seconds).toList()

                // then
                assertTrue(evaluations.isNotEmpty(), "Expected at least one work evaluation sample.")
                println("Sample: " + evaluations.size + " " + evaluations.last())
            }
        }

    @Test
    fun `should evaluate DEV network worker`() =
        runTest {
            withContext(Dispatchers.Default) {
                // when
                val evaluations = worker.evaluate(AttoNetwork.DEV, 2.seconds).toList()

                // then
                assertTrue(evaluations.isNotEmpty(), "Expected at least one work evaluation sample.")
                println("Sample: " + evaluations.size + " " + evaluations.last())
            }
        }

    @Test
    fun `should evaluate LOCAL network worker`() =
        runTest {
            withContext(Dispatchers.Default) {
                // when
                val evaluations = worker.evaluate(AttoNetwork.LOCAL, 2.seconds).toList()

                // then
                assertTrue(evaluations.isNotEmpty(), "Expected at least one work evaluation sample.")
                println("Sample: " + evaluations.size + " " + evaluations.last())
            }
        }

    @Test
    fun `should stop evaluating when work exceeds max duration`() =
        runTest {
            val evaluations =
                withContext(Dispatchers.Default) {
                    withTimeout(1.seconds) {
                        NeverCompletingWorker().evaluate(AttoNetwork.LOCAL, 50.milliseconds).toList()
                    }
                }

            assertTrue(evaluations.isEmpty(), "Expected no samples when no work completes before maxDuration.")
        }

    private class NeverCompletingWorker : AttoWorker {
        override suspend fun work(
            threshold: ULong,
            target: AttoWorkTarget,
        ): AttoWork = awaitCancellation()

        override fun close() = Unit
    }
}
