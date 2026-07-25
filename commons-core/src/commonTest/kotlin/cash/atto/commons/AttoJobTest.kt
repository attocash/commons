package cash.atto.commons

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttoJobTest {
    @Test
    fun `should delegate lifecycle operations`() {
        // Given
        var active = true
        var cancellations = 0
        val job =
            AttoJob.create(
                activeProvider = { active },
                cancellation = {
                    active = false
                    cancellations++
                },
            )

        assertTrue(job.isActive())

        // When
        job.cancel()

        // Then
        assertFalse(job.isActive())
        assertEquals(1, cancellations)
    }

    @Test
    fun `should await cancellation completion`() =
        runTest {
            // Given
            var active = true
            val cancellationCompleted = CompletableDeferred<Unit>()
            val job =
                AttoJob.create(
                    activeProvider = { active },
                    cancellation = { active = false },
                    cancellationAndJoin = {
                        active = false
                        cancellationCompleted.await()
                    },
                )

            // When
            val cancellation = backgroundScope.launch { job.cancelAndJoin() }

            // Then
            assertTrue(cancellation.isActive)
            cancellationCompleted.complete(Unit)
            cancellation.join()
            assertFalse(job.isActive())
            assertTrue(cancellation.isCompleted)
        }

    @Test
    fun `should allow repeated cancel and join`() =
        runTest {
            // Given
            var active = true
            var cancellations = 0
            val job =
                AttoJob.create(
                    activeProvider = { active },
                    cancellation = { active = false },
                    cancellationAndJoin = {
                        active = false
                        cancellations++
                    },
                )

            // When
            job.cancelAndJoin()
            job.cancelAndJoin()

            // Then
            assertFalse(job.isActive())
            assertEquals(2, cancellations)
        }
}
