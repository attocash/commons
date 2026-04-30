package cash.atto.commons.worker

import cash.atto.commons.AttoNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertTrue
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
                println(evaluations.joinToString("\n"))
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
                println(evaluations.joinToString("\n"))
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
                println(evaluations.joinToString("\n"))
            }
        }
}
