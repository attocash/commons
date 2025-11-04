package cash.atto.commons.node.monitor

import cash.atto.commons.AddressSupport
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.HeightSupport
import cash.atto.commons.node.AccountHeightSearch
import cash.atto.commons.node.HeightSearch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class AttoHeightMonitor<T> internal constructor(
    private val accountMonitor: AttoAccountMonitor,
    private val heightProvider: suspend (AttoAddress) -> AttoHeight = { AttoHeight.MIN },
) where T : HeightSupport, T : AddressSupport {
    private val mutex = Mutex()
    private val heightMap = mutableMapOf<AttoAddress, AttoHeight>()

    class Message<T>(
        val value: T,
        private val acknowledge: suspend () -> Unit,
    ) {
        suspend fun acknowledge() {
            acknowledge.invoke()
        }
    }

    internal abstract fun stream(search: HeightSearch): Flow<T>

    @OptIn(ExperimentalCoroutinesApi::class)
    fun stream(): Flow<Message<T>> {
        return accountMonitor
            .membershipFlow()
            .flatMapLatest { addresses ->
                if (addresses.isEmpty()) {
                    return@flatMapLatest emptyFlow()
                }
                val search =
                    mutex.withLock {
                        heightMap.keys.retainAll(addresses)

                        (addresses - heightMap.keys).forEach { address -> heightMap[address] = heightProvider.invoke(address) }

                        heightMap.toHeightSearch()
                    }
                return@flatMapLatest stream(search)
            }.map {
                val address = it.address
                val height = it.height
                Message(it) {
                    mutex.withLock {
                        heightMap[AttoAddress(address.algorithm, address.publicKey)] = height
                    }
                }
            }
    }

    private fun Map<AttoAddress, AttoHeight>.toHeightSearch(): HeightSearch {
        val accounts =
            this.map {
                AccountHeightSearch(it.key, it.value)
            }

        return HeightSearch(accounts)
    }
}
