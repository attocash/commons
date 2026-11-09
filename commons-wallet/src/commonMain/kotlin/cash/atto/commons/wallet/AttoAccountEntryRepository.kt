package cash.atto.commons.wallet

import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import kotlinx.coroutines.flow.Flow

interface AttoAccountEntryRepository : AutoCloseable {
    companion object {
        fun inMemory(): AttoAccountEntryRepository = AttoInMemoryAccountEntryRepository()
    }

    suspend fun save(entry: AttoAccountEntry)

    suspend fun stream(address: AttoAddress): Flow<AttoAccountEntry>

    suspend fun last(address: AttoAddress): AttoAccountEntry?

    override fun close() {}
}

private class AttoInMemoryAccountEntryRepository : AttoAccountEntryRepository {
    private val history = InMemoryAccountHistory<AttoAccountEntry> { it.address }

    override suspend fun save(entry: AttoAccountEntry) = history.save(entry)

    override suspend fun stream(address: AttoAddress): Flow<AttoAccountEntry> = history.stream(address)

    override suspend fun last(address: AttoAddress): AttoAccountEntry? = history.last(address)
}

@Deprecated(
    "Moved to AttoAccountEntryRepository.inMemory(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoAccountEntryRepository.inMemory()"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoAccountEntryRepository.Companion.inMemory(): AttoAccountEntryRepository = AttoAccountEntryRepository.inMemory()
