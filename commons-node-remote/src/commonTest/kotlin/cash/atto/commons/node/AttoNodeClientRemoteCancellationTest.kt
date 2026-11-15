package cash.atto.commons.node

import cash.atto.commons.AttoAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class AttoNodeClientRemoteCancellationTest {
    @Test
    fun `membership self cancellation propagates before and after its first item even when input returns`() =
        runTest {
            for (afterFirst in listOf(false, true)) {
                for (returnsNormally in listOf(false, true)) {
                    // Given
                    val started = CompletableDeferred<Unit>()
                    val expected = CancellationException("Membership cancelled: afterFirst=$afterFirst, returnsNormally=$returnsNormally")
                    val memberships =
                        flow<Collection<AttoAddress>> {
                            if (afterFirst) {
                                emit(emptyList())
                                started.await()
                            }
                            currentCoroutineContext().cancel(expected)
                            if (!returnsNormally) awaitCancellation()
                        }
                    val client =
                        AttoNodeClient.remote("http://127.0.0.1:1", AttoNodeStreamTransport.WEBSOCKET) {
                            started.complete(Unit)
                            awaitCancellation()
                        }

                    // When
                    val failure =
                        withTimeout(2.seconds) {
                            assertFailsWith<CancellationException> { client.accountStream(memberships).collect() }
                        }

                    // Then
                    assertEquals(expected.message, failure.message)
                }
            }
        }

    @Test
    fun `membership timeout before first request cancels the stream`() =
        runTest {
            // Given
            val expected = CompletableDeferred<TimeoutCancellationException>()
            val memberships =
                flow<Collection<AttoAddress>> {
                    try {
                        withTimeout(1.seconds) { awaitCancellation() }
                    } catch (failure: TimeoutCancellationException) {
                        expected.complete(failure)
                        throw failure
                    }
                }
            val client = AttoNodeClient.remote("http://127.0.0.1:1", AttoNodeStreamTransport.WEBSOCKET)

            // When
            val failure =
                withTimeout(2.seconds) {
                    assertFailsWith<TimeoutCancellationException> { client.accountStream(memberships).collect() }
                }

            // Then
            assertEquals(expected.await().message, failure.message)
        }

    @Test
    fun `header provider timeout fails the stream without a completion message`() =
        runTest {
            // Given
            val expected = CompletableDeferred<TimeoutCancellationException>()
            val client =
                AttoNodeClient.remote("http://127.0.0.1:1", AttoNodeStreamTransport.WEBSOCKET) {
                    try {
                        withTimeout(1.seconds) { awaitCancellation() }
                    } catch (failure: TimeoutCancellationException) {
                        expected.complete(failure)
                        throw failure
                    }
                }

            // When
            val failure =
                withTimeout(2.seconds) {
                    assertFailsWith<TimeoutCancellationException> { client.accountStream().collect() }
                }

            // Then
            assertEquals(expected.await().message, failure.message)
        }
}
