package cash.atto.commons.worker

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class OpenCLResourcesTest {
    @Test
    fun `failed initialization releases acquired resources in reverse order`() {
        // Given
        val resources = OpenCLResources()
        val released = mutableListOf<String>()
        val allocationFailure = IllegalStateException("Allocation failed")

        // When
        val failure =
            assertFailsWith<IllegalStateException> {
                resources.use {
                    it.acquire("first") { released.add(it) }
                    it.acquire("second") { released.add(it) }
                    throw allocationFailure
                }
            }

        // Then
        assertSame(allocationFailure, failure)
        assertEquals(listOf("second", "first"), released)
    }

    @Test
    fun `successful initialization retains resources until close`() {
        // Given
        val resources = OpenCLResources()
        val released = mutableListOf<String>()

        // When
        val handle = resources.acquire("retained") { released.add(it) }

        // Then
        assertEquals("retained", handle)
        assertEquals(emptyList(), released)
        resources.close()
        resources.close()
        assertEquals(listOf("retained"), released)
    }

    @Test
    fun `operation failure retains its identity when cleanup fails`() {
        // Given
        val operationFailure = IllegalStateException("Operation failed")
        val cleanupFailure = IllegalArgumentException("Cleanup failed")
        val released = mutableListOf<String>()

        // When
        val failure =
            assertFailsWith<IllegalStateException> {
                OpenCLResources().use { resources ->
                    resources.acquire("first") { released.add(it) }
                    resources.acquire("second") {
                        released.add(it)
                        throw cleanupFailure
                    }
                    throw operationFailure
                }
            }

        // Then
        assertSame(operationFailure, failure)
        assertEquals(listOf("second", "first"), released)
        assertSame(cleanupFailure, failure.suppressedExceptions.single())
    }

    @Test
    fun `all cleanup failures are retained after every release is attempted`() {
        // Given
        val resources = OpenCLResources()
        val firstFailure = IllegalStateException("First acquired")
        val secondFailure = IllegalStateException("Second acquired")
        resources.acquire("first") { throw firstFailure }
        resources.acquire("second") { throw secondFailure }

        // When
        val failure = assertFailsWith<IllegalStateException> { resources.close() }

        // Then
        assertSame(secondFailure, failure)
        assertSame(firstFailure, failure.suppressedExceptions.single())
        resources.close()
    }

    @Test
    fun `initialization failure retains its identity when cleanup fails`() {
        // Given
        val resources = OpenCLResources()
        val allocationFailure = IllegalStateException("Allocation failed")
        val cleanupFailure = IllegalArgumentException("Cleanup failed")

        // When
        val failure =
            assertFailsWith<IllegalStateException> {
                resources.acquire("first") { throw cleanupFailure }
                resources.closeOnFailure(allocationFailure)
            }

        // Then
        assertSame(allocationFailure, failure)
        assertSame(cleanupFailure, failure.suppressedExceptions.single())
    }

    @Test
    fun `cancellation remains primary while all resources are released`() {
        // Given
        val cancellation = CancellationException("Work cancelled")
        val cleanupFailure = IllegalStateException("Cleanup failed")
        val released = mutableListOf<String>()

        // When
        val failure =
            assertFailsWith<CancellationException> {
                OpenCLResources().use { resources ->
                    resources.acquire("first") { released.add(it) }
                    resources.acquire("second") {
                        released.add(it)
                        throw cleanupFailure
                    }
                    throw cancellation
                }
            }

        // Then
        assertSame(cancellation, failure)
        assertEquals(listOf("second", "first"), released)
        assertSame(cleanupFailure, failure.suppressedExceptions.single())
    }
}
