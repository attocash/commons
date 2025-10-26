package cash.atto.commons.wallet

import cash.atto.commons.AttoWork
import kotlinx.coroutines.flow.MutableStateFlow

@Deprecated("Use AttoWorkerCache instead")
interface AttoWorkCache : AutoCloseable {
    companion object {}

    suspend fun save(work: AttoWork)

    suspend fun get(): AttoWork?

    override fun close() {}
}

private class AttoInMemoryWorkCache : AttoWorkCache {
    private val workState = MutableStateFlow<AttoWork?>(null)

    override suspend fun save(work: AttoWork) {
        workState.value = work
    }

    override suspend fun get(): AttoWork? = workState.value
}

fun AttoWorkCache.Companion.inMemory(): AttoWorkCache = AttoInMemoryWorkCache()
