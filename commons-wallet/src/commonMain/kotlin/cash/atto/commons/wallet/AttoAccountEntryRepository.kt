package cash.atto.commons.wallet

import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoPublicKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AttoAccountEntryRepository : AutoCloseable {
    companion object {}

    suspend fun save(entry: AttoAccountEntry)

    suspend fun stream(publicKey: AttoPublicKey): Flow<AttoAccountEntry>

    suspend fun last(publicKey: AttoPublicKey): AttoAccountEntry?

    override fun close() {}
}

private class AttoInMemoryAccountEntryRepository : AttoAccountEntryRepository {
    private val mutex = Mutex()
    private val entryMap = mutableMapOf<AttoPublicKey, MutableList<AttoAccountEntry>>()
    private val flow = MutableSharedFlow<AttoAccountEntry>()

    override suspend fun save(entry: AttoAccountEntry) {
        val publicKey = entry.publicKey
        mutex.withLock {
            val entries = entryMap[publicKey] ?: mutableListOf()
            entries.add(entry)
            entryMap[publicKey] = entries
            flow.emit(entry)
        }
    }

    override suspend fun stream(publicKey: AttoPublicKey): Flow<AttoAccountEntry> {
        val cacheFlow =
            flow {
                mutex.withLock {
                    val transactions = entryMap[publicKey]?.toList() ?: emptyList()
                    emitAll(transactions.asFlow())
                }
            }

        val liveFlow = flow.filter { it.publicKey == publicKey }

        return merge(cacheFlow, liveFlow)
    }

    override suspend fun last(publicKey: AttoPublicKey): AttoAccountEntry? = entryMap[publicKey]?.lastOrNull()
}

fun AttoAccountEntryRepository.Companion.inMemory(): AttoAccountEntryRepository = AttoInMemoryAccountEntryRepository()
