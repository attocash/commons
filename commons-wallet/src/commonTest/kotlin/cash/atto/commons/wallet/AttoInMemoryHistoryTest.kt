package cash.atto.commons.wallet

import cash.atto.commons.AttoAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttoInMemoryHistoryTest {
    @Test
    fun `transaction history preserves replay and concurrent appends for slow subscribers`() =
        runTest {
            // Given
            val repository = AttoTransactionRepository.inMemory()
            val values = (1..3).map { WalletTestData.transaction(it) }
            val foreign = WalletTestData.transaction(1, WalletTestData.foreignAddress)

            // When
            verifySlowReplay(repository::save, repository::stream, repository::last, values, foreign)

            // Then
            assertEquals(values.last(), repository.last(WalletTestData.address))
        }

    @Test
    fun `account entry history preserves replay and concurrent appends for slow subscribers`() =
        runTest {
            // Given
            val repository = AttoAccountEntryRepository.inMemory()
            val values = (1..3).map { WalletTestData.entry(it) }
            val foreign = WalletTestData.entry(1, WalletTestData.foreignAddress)

            // When
            verifySlowReplay(repository::save, repository::stream, repository::last, values, foreign)

            // Then
            assertEquals(values.last(), repository.last(WalletTestData.address))
        }

    @Test
    fun `transaction history cancellation releases replay without blocking writes`() =
        runTest {
            // Given
            val repository = AttoTransactionRepository.inMemory()
            val values = (1..3).map { WalletTestData.transaction(it) }

            // When
            verifyCancellation(repository::save, repository::stream, repository::last, values)

            // Then
            assertEquals(values.last(), repository.last(WalletTestData.address))
        }

    @Test
    fun `account entry history cancellation releases replay without blocking writes`() =
        runTest {
            // Given
            val repository = AttoAccountEntryRepository.inMemory()
            val values = (1..3).map { WalletTestData.entry(it) }

            // When
            verifyCancellation(repository::save, repository::stream, repository::last, values)

            // Then
            assertEquals(values.last(), repository.last(WalletTestData.address))
        }

    private suspend fun <T> TestScope.verifySlowReplay(
        save: suspend (T) -> Unit,
        stream: suspend (AttoAddress) -> Flow<T>,
        last: suspend (AttoAddress) -> T?,
        values: List<T>,
        foreign: T,
    ) {
        // Given
        assertNull(last(WalletTestData.address))
        save(values[0])
        save(values[1])
        save(foreign)
        val replayStarted = CompletableDeferred<Unit>()
        val releaseReplay = CompletableDeferred<Unit>()
        val received = mutableListOf<T>()
        val slowSubscriber =
            backgroundScope.launch {
                stream(WalletTestData.address).take(3).collect {
                    received.add(it)
                    if (received.size == 1) {
                        replayStarted.complete(Unit)
                        releaseReplay.await()
                    }
                }
            }
        runCurrent()
        assertTrue(replayStarted.isCompleted)

        // When: appends and another subscriber proceed while replay is suspended.
        val append = launch { save(values[2]) }
        runCurrent()
        assertTrue(append.isCompleted)
        assertEquals(values[2], last(WalletTestData.address))
        assertEquals(listOf(foreign), stream(WalletTestData.foreignAddress).take(1).toList())
        val secondSubscriber = stream(WalletTestData.address).take(3).toList()
        releaseReplay.complete(Unit)
        slowSubscriber.join()

        // Then
        assertEquals(values, received)
        assertEquals(values, secondSubscriber)
    }

    private suspend fun <T> TestScope.verifyCancellation(
        save: suspend (T) -> Unit,
        stream: suspend (AttoAddress) -> Flow<T>,
        last: suspend (AttoAddress) -> T?,
        values: List<T>,
    ) {
        // Given
        save(values[0])
        save(values[1])
        val blocked = CompletableDeferred<Unit>()
        val subscriber =
            backgroundScope.launch {
                stream(WalletTestData.address).collect { blocked.await() }
            }
        runCurrent()

        // When
        subscriber.cancelAndJoin()
        save(values[2])
        save(values[2])

        // Then: cancellation does not consume history, including repeated saves.
        assertEquals(values + values[2], stream(WalletTestData.address).take(4).toList())
        assertEquals(values[2], last(WalletTestData.address))
    }
}
