package cash.atto.commons.node

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StreamConsumptionTest {
    @Test
    fun `stream errors should reach the callback and leave the scope usable`() =
        runTest {
            // Given: Ktor wraps a rejected JavaScript fetch in a Kotlin Error.
            val failure = Error("Fail to fetch")
            val completions = mutableListOf<Throwable?>()

            // When
            consumeStream(flow<Int> { throw failure }, {}, { completions.add(it) })
            runCurrent()
            consumeStream(emptyFlow<Int>(), {}, { completions.add(it) })
            runCurrent()

            // Then: deliver the original failure and allow another subscription.
            assertEquals(2, completions.size)
            assertSame(failure, completions[0])
            assertNull(completions[1])
        }

    @Test
    fun `stream exceptions should reach the callback unchanged`() =
        runTest {
            // Given
            val failure = IllegalStateException("Stream failed")
            val completions = mutableListOf<Throwable?>()

            // When
            consumeStream(flow<Int> { throw failure }, {}, { completions.add(it) })
            runCurrent()

            // Then
            assertSame(failure, completions.single())
        }

    @Test
    fun `observer errors should reach the callback unchanged`() =
        runTest {
            // Given
            val failure = Error("Observer failed")
            val completions = mutableListOf<Throwable?>()

            // When
            consumeStream(flowOf(1), { throw failure }, { completions.add(it) })
            runCurrent()

            // Then
            assertSame(failure, completions.single())
        }

    @Test
    fun `completed streams should deliver events and report success once`() =
        runTest {
            // Given
            val values = mutableListOf<Int>()
            val completions = mutableListOf<Throwable?>()

            // When
            val job = consumeStream(flowOf(1, 2), { values.add(it) }, { completions.add(it) })
            runCurrent()

            // Then
            assertEquals(listOf(1, 2), values)
            assertNull(completions.single())
            assertFalse(job.isActive())
        }

    @Test
    fun `cancelled streams should report no error and await cleanup`() =
        runTest {
            // Given
            var cleanedUp = false
            val completions = mutableListOf<Throwable?>()
            val stream =
                flow<Int> {
                    try {
                        awaitCancellation()
                    } finally {
                        cleanedUp = true
                    }
                }
            val job = consumeStream(stream, {}, { completions.add(it) })
            runCurrent()

            // When
            job.cancelAndJoin()

            // Then
            assertNull(completions.single())
            assertTrue(cleanedUp)
            assertFalse(job.isActive())
        }
}
