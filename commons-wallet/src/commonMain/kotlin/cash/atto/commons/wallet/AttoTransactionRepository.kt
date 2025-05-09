package cash.atto.commons.wallet

import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AttoTransactionRepository : AutoCloseable {
    companion object {}

    suspend fun save(transaction: AttoTransaction)

    suspend fun stream(publicKey: AttoPublicKey): Flow<AttoTransaction>

    suspend fun last(publicKey: AttoPublicKey): AttoTransaction?

    override fun close() {}
}

private class AttoInMemoryTransactionRepository : AttoTransactionRepository {
    private val mutex = Mutex()
    private val transactionMap = mutableMapOf<AttoPublicKey, MutableList<AttoTransaction>>()
    private val flow = MutableSharedFlow<AttoTransaction>()

    override suspend fun save(transaction: AttoTransaction) {
        val publicKey = transaction.block.publicKey
        mutex.withLock {
            val transactions = transactionMap[publicKey] ?: mutableListOf()
            transactions.add(transaction)
            transactionMap[publicKey] = transactions
            flow.emit(transaction)
        }
    }

    override suspend fun stream(publicKey: AttoPublicKey): Flow<AttoTransaction> {
        val cacheFlow =
            flow {
                mutex.withLock {
                    val transactions = transactionMap[publicKey]?.toList() ?: emptyList()
                    emitAll(transactions.asFlow())
                }
            }

        val liveFlow = flow.filter { it.block.publicKey == publicKey }

        return merge(cacheFlow, liveFlow)
    }

    override suspend fun last(publicKey: AttoPublicKey): AttoTransaction? = transactionMap[publicKey]?.lastOrNull()
}

fun AttoTransactionRepository.Companion.inMemory(): AttoTransactionRepository = AttoInMemoryTransactionRepository()
