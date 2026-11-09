package cash.atto.commons.wallet

import cash.atto.commons.AttoAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InMemoryAccountHistory<T>(
    private val addressOf: (T) -> AttoAddress,
) {
    private class History<T> {
        val values = mutableListOf<T>()
        val size = MutableStateFlow(0)
    }

    private val mutex = Mutex()
    private val histories = mutableMapOf<AttoAddress, History<T>>()

    suspend fun save(value: T) {
        mutex.withLock {
            val history = histories.getOrPut(addressOf(value)) { History() }
            history.values.add(value)
            history.size.value = history.values.size
        }
    }

    fun stream(address: AttoAddress): Flow<T> =
        flow {
            val history = mutex.withLock { histories.getOrPut(address) { History() } }
            var nextIndex = 0
            history.size.collect { size ->
                // The append-only history retains values even when size notifications are conflated.
                val pending = mutex.withLock { history.values.subList(nextIndex, size).toList() }
                for (value in pending) {
                    emit(value)
                    nextIndex++
                }
            }
        }

    suspend fun last(address: AttoAddress): T? = mutex.withLock { histories[address]?.values?.lastOrNull() }
}
