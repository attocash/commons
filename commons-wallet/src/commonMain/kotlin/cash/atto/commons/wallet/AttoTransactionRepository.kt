package cash.atto.commons.wallet

import cash.atto.commons.AttoAddress
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
    companion object {
        fun inMemory(): AttoTransactionRepository = AttoInMemoryTransactionRepository()
    }

    suspend fun save(transaction: AttoTransaction)

    suspend fun stream(address: AttoAddress): Flow<AttoTransaction>

    suspend fun last(address: AttoAddress): AttoTransaction?

    override fun close() {}
}

private class AttoInMemoryTransactionRepository : AttoTransactionRepository {
    private val mutex = Mutex()
    private val transactionMap = mutableMapOf<AttoAddress, MutableList<AttoTransaction>>()
    private val flow = MutableSharedFlow<AttoTransaction>()

    override suspend fun save(transaction: AttoTransaction) {
        val address = transaction.block.address
        mutex.withLock {
            val transactions = transactionMap[address] ?: mutableListOf()
            transactions.add(transaction)
            transactionMap[address] = transactions
            flow.emit(transaction)
        }
    }

    override suspend fun stream(address: AttoAddress): Flow<AttoTransaction> {
        val cacheFlow =
            flow {
                mutex.withLock {
                    val transactions = transactionMap[address]?.toList() ?: emptyList()
                    emitAll(transactions.asFlow())
                }
            }

        val liveFlow = flow.filter { it.block.address == address }

        return merge(cacheFlow, liveFlow)
    }

    override suspend fun last(address: AttoAddress): AttoTransaction? = transactionMap[address]?.lastOrNull()
}

@Deprecated(
    "Moved to AttoTransactionRepository.inMemory(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoTransactionRepository.inMemory()"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoTransactionRepository.Companion.inMemory(): AttoTransactionRepository = AttoTransactionRepository.inMemory()
