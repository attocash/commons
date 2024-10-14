package cash.atto.commons.wallet

import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AttoTransactionRepository : AutoCloseable {
    companion object {}

    suspend fun save(transaction: AttoTransaction)
    suspend fun list(publicKey: AttoPublicKey): List<AttoTransaction>
    suspend fun last(publicKey: AttoPublicKey): AttoTransaction?

    override fun close() {}
}

private class AttoInMemoryTransactionRepository : AttoTransactionRepository {
    private val mutex = Mutex()
    private val transactionMap = mutableMapOf<AttoPublicKey, MutableList<AttoTransaction>>()

    override suspend fun save(transaction: AttoTransaction) {
        val publicKey = transaction.block.publicKey
        mutex.withLock {
            val transactions = transactionMap[publicKey] ?: mutableListOf()
            transactions.add(transaction)
            transactionMap[publicKey] = transactions
        }
    }

    override suspend fun list(publicKey: AttoPublicKey): List<AttoTransaction> {
        mutex.withLock {
            return transactionMap[publicKey]?.toList() ?: emptyList()
        }
    }

    override suspend fun last(publicKey: AttoPublicKey): AttoTransaction? {
        return list(publicKey).lastOrNull()
    }

}

fun AttoTransactionRepository.Companion.inMemory(): AttoTransactionRepository = AttoInMemoryTransactionRepository()
