package cash.atto.commons.wallet

import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoPublicKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AttoAccountEntryRepository : AutoCloseable {
    companion object {}

    suspend fun save(entry: AttoAccountEntry)
    suspend fun list(publicKey: AttoPublicKey): List<AttoAccountEntry>
    suspend fun last(publicKey: AttoPublicKey): AttoAccountEntry?

    override fun close() {}
}

private class AttoInMemoryAccountEntryRepository : AttoAccountEntryRepository {
    private val mutex = Mutex()
    private val transactionMap = mutableMapOf<AttoPublicKey, MutableList<AttoAccountEntry>>()

    override suspend fun save(entry: AttoAccountEntry) {
        val publicKey = entry.publicKey
        mutex.withLock {
            val entries = transactionMap[publicKey] ?: mutableListOf()
            entries.add(entry)
            transactionMap[publicKey] = entries
        }
    }

    override suspend fun list(publicKey: AttoPublicKey): List<AttoAccountEntry> {
        mutex.withLock {
            return transactionMap[publicKey]?.toList() ?: emptyList()
        }
    }

    override suspend fun last(publicKey: AttoPublicKey): AttoAccountEntry? {
        return list(publicKey).lastOrNull()
    }

}

fun AttoAccountEntryRepository.Companion.inMemory(): AttoAccountEntryRepository = AttoInMemoryAccountEntryRepository()
