@file:Suppress("DEPRECATION")

package cash.atto.commons.wallet

import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttoWalletViewerPersistenceTest {
    @Test
    fun `viewer persists immediate values without observers`() =
        runTest {
            // Given
            val entry = WalletTestData.entry(1)
            val transaction = WalletTestData.transaction(1)
            val entries = AttoAccountEntryRepository.inMemory()
            val transactions = AttoTransactionRepository.inMemory()
            val client =
                object : WalletTestNodeClient() {
                    override fun accountEntryStream(
                        address: AttoAddress,
                        fromHeight: AttoHeight,
                        toHeight: AttoHeight?,
                    ): Flow<AttoAccountEntry> = flowOf(entry)

                    override fun transactionStream(
                        address: AttoAddress,
                        fromHeight: AttoHeight,
                        toHeight: AttoHeight?,
                    ): Flow<AttoTransaction> =
                        flow {
                            emit(transaction)
                            awaitCancellation()
                        }
                }
            val viewer = AttoWalletViewer(WalletTestData.address, client, entries, transactions, backgroundScope)

            // When
            viewer.start()
            runCurrent()

            // Then
            assertEquals(entry, entries.last(WalletTestData.address))
            assertEquals(transaction, transactions.last(WalletTestData.address))
            viewer.close()
        }

    @Test
    fun `viewer publishes persisted facts and resumes from the last saved height`() =
        runTest {
            // Given
            val entry = WalletTestData.entry(5)
            val transaction = WalletTestData.transaction(8)
            val entries = AttoAccountEntryRepository.inMemory()
            val transactions = AttoTransactionRepository.inMemory()
            entries.save(WalletTestData.entry(4))
            transactions.save(WalletTestData.transaction(7))
            val entryRequests = mutableListOf<AttoHeight>()
            val transactionRequests = mutableListOf<AttoHeight>()
            val client =
                object : WalletTestNodeClient() {
                    override fun accountEntryStream(
                        address: AttoAddress,
                        fromHeight: AttoHeight,
                        toHeight: AttoHeight?,
                    ): Flow<AttoAccountEntry> {
                        entryRequests.add(fromHeight)
                        return flowOf(entry)
                    }

                    override fun transactionStream(
                        address: AttoAddress,
                        fromHeight: AttoHeight,
                        toHeight: AttoHeight?,
                    ): Flow<AttoTransaction> {
                        transactionRequests.add(fromHeight)
                        return flow {
                            if (transactionRequests.size == 1) emit(transaction) else awaitCancellation()
                        }
                    }
                }
            val viewer = AttoWalletViewer(WalletTestData.address, client, entries, transactions, backgroundScope)
            val observedEntries = mutableListOf<AttoAccountEntry?>()
            val observedTransactions = mutableListOf<AttoTransaction?>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewer.accountEntryFlow.collect { observedEntries.add(entries.last(it.address)) }
            }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewer.transactionFlow.collect { observedTransactions.add(transactions.last(it.address)) }
            }

            // When
            viewer.start()
            runCurrent()

            // Then: entries stop normally; transactions reconnect from the completed save.
            assertEquals(listOf(entry.height), entryRequests)
            assertEquals(listOf(transaction.height, transaction.height + 1U), transactionRequests)
            assertEquals(listOf<AttoAccountEntry?>(entry), observedEntries)
            assertEquals(listOf<AttoTransaction?>(transaction), observedTransactions)
            viewer.close()
        }

    @Test
    fun `viewer retries each failed save before publishing the value`() =
        runTest {
            // Given
            val entry = WalletTestData.entry(1)
            val transaction = WalletTestData.transaction(1)
            val entryStorage = AttoAccountEntryRepository.inMemory()
            val transactionStorage = AttoTransactionRepository.inMemory()
            var entryAttempts = 0
            var transactionAttempts = 0
            val entries =
                object : AttoAccountEntryRepository by entryStorage {
                    override suspend fun save(entry: AttoAccountEntry) {
                        if (++entryAttempts == 1) error("Storage temporarily unavailable")
                        entryStorage.save(entry)
                    }
                }
            val transactions =
                object : AttoTransactionRepository by transactionStorage {
                    override suspend fun save(transaction: AttoTransaction) {
                        if (++transactionAttempts == 1) error("Storage temporarily unavailable")
                        transactionStorage.save(transaction)
                    }
                }
            val client =
                object : WalletTestNodeClient() {
                    override fun accountEntryStream(
                        address: AttoAddress,
                        fromHeight: AttoHeight,
                        toHeight: AttoHeight?,
                    ): Flow<AttoAccountEntry> = flowOf(entry)

                    override fun transactionStream(
                        address: AttoAddress,
                        fromHeight: AttoHeight,
                        toHeight: AttoHeight?,
                    ): Flow<AttoTransaction> =
                        flow {
                            emit(transaction)
                            awaitCancellation()
                        }
                }
            val viewer = AttoWalletViewer(WalletTestData.address, client, entries, transactions, backgroundScope)
            val observedEntries = mutableListOf<AttoAccountEntry>()
            val observedTransactions = mutableListOf<AttoTransaction>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewer.accountEntryFlow.collect {
                    assertEquals(it, entries.last(it.address))
                    observedEntries.add(it)
                }
            }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewer.transactionFlow.collect {
                    assertEquals(it, transactions.last(it.address))
                    observedTransactions.add(it)
                }
            }

            // When
            viewer.start()
            runCurrent()
            assertTrue(observedEntries.isEmpty())
            assertTrue(observedTransactions.isEmpty())
            assertEquals(1, entryAttempts)
            assertEquals(1, transactionAttempts)
            advanceTimeBy(10_000)
            runCurrent()

            // Then
            assertEquals(2, entryAttempts)
            assertEquals(2, transactionAttempts)
            assertEquals(listOf(entry), observedEntries)
            assertEquals(listOf(transaction), observedTransactions)
            viewer.close()
        }

    @Test
    fun `viewer retries failed streams from completed persistence`() =
        runTest {
            // Given
            val entries = AttoAccountEntryRepository.inMemory()
            val transactions = AttoTransactionRepository.inMemory()
            val entryRequests = mutableListOf<AttoHeight>()
            val transactionRequests = mutableListOf<AttoHeight>()
            val entry = WalletTestData.entry(1)
            val transaction = WalletTestData.transaction(1)
            val client =
                object : WalletTestNodeClient() {
                    override fun accountEntryStream(
                        address: AttoAddress,
                        fromHeight: AttoHeight,
                        toHeight: AttoHeight?,
                    ): Flow<AttoAccountEntry> {
                        entryRequests.add(fromHeight)
                        return flow {
                            if (entryRequests.size == 1) {
                                emit(entry)
                                error("Connection interrupted")
                            }
                            awaitCancellation()
                        }
                    }

                    override fun transactionStream(
                        address: AttoAddress,
                        fromHeight: AttoHeight,
                        toHeight: AttoHeight?,
                    ): Flow<AttoTransaction> {
                        transactionRequests.add(fromHeight)
                        return flow {
                            if (transactionRequests.size == 1) {
                                emit(transaction)
                                error("Connection interrupted")
                            }
                            awaitCancellation()
                        }
                    }
                }
            val viewer = AttoWalletViewer(WalletTestData.address, client, entries, transactions, backgroundScope)

            // When
            viewer.start()
            runCurrent()
            assertEquals(listOf(entry.height), entryRequests)
            assertEquals(listOf(transaction.height), transactionRequests)
            advanceTimeBy(10_000)
            runCurrent()

            // Then
            assertEquals(listOf(entry.height, entry.height + 1U), entryRequests)
            assertEquals(listOf(transaction.height, transaction.height + 1U), transactionRequests)
            assertEquals(entry, entries.last(WalletTestData.address))
            assertEquals(transaction, transactions.last(WalletTestData.address))
            viewer.close()
        }

    @Test
    fun `closing viewer cancels pending persistence retries and retains injected repositories`() =
        runTest {
            // Given
            var attempts = 0
            var closed = false
            var streamStopped = false
            val entries =
                object : AttoAccountEntryRepository by AttoAccountEntryRepository.inMemory() {
                    override suspend fun save(entry: AttoAccountEntry) {
                        attempts++
                        error("Storage unavailable")
                    }

                    override fun close() {
                        closed = true
                    }
                }
            val client =
                object : WalletTestNodeClient() {
                    override fun accountEntryStream(
                        address: AttoAddress,
                        fromHeight: AttoHeight,
                        toHeight: AttoHeight?,
                    ): Flow<AttoAccountEntry> =
                        flow {
                            try {
                                emit(WalletTestData.entry(1))
                            } finally {
                                streamStopped = true
                            }
                        }
                }
            val viewer = AttoWalletViewer(WalletTestData.address, client, entries, null, backgroundScope)
            viewer.start()
            runCurrent()

            // When
            viewer.close()
            runCurrent()
            advanceTimeBy(20_000)
            runCurrent()

            // Then
            assertEquals(1, attempts)
            assertTrue(streamStopped)
            assertTrue(!closed)
        }
}
