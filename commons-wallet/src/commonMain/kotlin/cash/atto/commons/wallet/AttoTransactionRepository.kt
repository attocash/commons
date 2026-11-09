package cash.atto.commons.wallet

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoTransaction
import kotlinx.coroutines.flow.Flow

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
    private val history = InMemoryAccountHistory<AttoTransaction> { it.address }

    override suspend fun save(transaction: AttoTransaction) = history.save(transaction)

    override suspend fun stream(address: AttoAddress): Flow<AttoTransaction> = history.stream(address)

    override suspend fun last(address: AttoAddress): AttoTransaction? = history.last(address)
}

@Deprecated(
    "Moved to AttoTransactionRepository.inMemory(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoTransactionRepository.inMemory()"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoTransactionRepository.Companion.inMemory(): AttoTransactionRepository = AttoTransactionRepository.inMemory()
