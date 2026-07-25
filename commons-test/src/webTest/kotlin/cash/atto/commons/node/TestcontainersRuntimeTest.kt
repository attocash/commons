package cash.atto.commons.node

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestcontainersRuntimeTest {
    @Test
    fun `should stop resources sequentially`() =
        runTest {
            // Given
            val stops = mutableListOf<String>()

            // When
            stopTestcontainersResources(
                listOf(
                    { stops += "node" },
                    { stops += "mysql" },
                    { stops += "network" },
                ),
            )

            // Then
            assertEquals(listOf("node", "mysql", "network"), stops)
        }

    @Test
    fun `should continue cleanup and preserve failures`() =
        runTest {
            // Given
            val stops = mutableListOf<String>()

            // When
            val failure =
                try {
                    stopTestcontainersResources(
                        listOf(
                            {
                                stops += "node"
                                error("node cleanup failed")
                            },
                            {
                                stops += "mysql"
                                error("mysql cleanup failed")
                            },
                            { stops += "network" },
                        ),
                    )
                    null
                } catch (exception: Throwable) {
                    exception
                }

            // Then
            assertEquals(listOf("node", "mysql", "network"), stops)
            assertEquals("node cleanup failed", failure?.message)
            assertEquals(listOf("mysql cleanup failed"), failure?.suppressedExceptions?.map { it.message })
        }

    @Test
    fun `should retry active endpoint cleanup failures`() =
        runTest {
            // Given
            var attempts = 0

            // When
            stopTestcontainersResources(
                listOf(
                    {
                        attempts++
                        if (attempts < 5) {
                            error("network still has active endpoints")
                        }
                    },
                ),
            )

            // Then
            assertEquals(5, attempts)
        }

    @Test
    fun `should serialize scheduled cleanup before lifecycle continuation`() =
        runTest {
            // Given
            val cleanupRelease = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            scheduleTestcontainersCleanup(
                listOf(
                    {
                        events += "cleanup started"
                        cleanupRelease.await()
                        events += "cleanup finished"
                    },
                ),
            )

            // When
            val lifecycleContinuation =
                async {
                    awaitScheduledTestcontainersCleanup()
                    events += "lifecycle continued"
                }
            yield()

            // Then
            assertFalse(lifecycleContinuation.isCompleted)
            cleanupRelease.complete(Unit)
            lifecycleContinuation.await()
            assertEquals(listOf("cleanup started", "cleanup finished", "lifecycle continued"), events)
        }

    @Test
    fun `should surface scheduled cleanup failure at next lifecycle boundary`() =
        runTest {
            // Given
            scheduleTestcontainersCleanup(listOf({ error("scheduled cleanup failed") }))

            // When
            val failure =
                try {
                    awaitScheduledTestcontainersCleanup()
                    null
                } catch (exception: Throwable) {
                    exception
                }

            // Then
            assertEquals("scheduled cleanup failed", failure?.message)
            awaitScheduledTestcontainersCleanup()
        }

    @Test
    fun `should reject duplicate starts until resources are released`() {
        // Given
        val lifecycle = TestcontainersLifecycle("Node mock")
        lifecycle.beginStart()

        // When
        val failure = assertFailsWith<IllegalStateException> { lifecycle.beginStart() }
        lifecycle.release()
        lifecycle.beginStart()

        // Then
        assertTrue(failure.message.orEmpty().contains("already started or starting"))
    }
}
