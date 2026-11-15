package cash.atto.commons.node.monitor

import cash.atto.commons.AddressSupport
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.HeightSupport
import cash.atto.commons.node.AccountHeightSearch
import cash.atto.commons.node.HeightSearch
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}
private val RETRY_DELAY = 10.seconds

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
    internal open fun stream(searches: Flow<HeightSearch>): Flow<T> = searches.flatMapLatest { stream(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun stream(): Flow<Message<T>> {
        val values =
            if (accountMonitor.client.supportsSubscriptionUpdates) {
                retryingDynamicStream()
            } else {
                accountMonitor
                    .membershipFlow()
                    .flatMapLatest { addresses ->
                        if (addresses.isEmpty()) {
                            return@flatMapLatest emptyFlow()
                        }
                        return@flatMapLatest retryingStream(addresses)
                    }
            }
        return values.map {
            val address = it.address
            val height = it.height
            Message(it) {
                mutex.withLock {
                    heightMap[AttoAddress(address.algorithm, address.publicKey)] = height
                }
            }
        }
    }

    private fun retryingDynamicStream(): Flow<T> =
        flow {
            while (true) {
                try {
                    emitAll(stream(dynamicSearches()).filter { accountMonitor.isMonitored(it.address) })
                    return@flow
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to stream monitor values. Retrying in $RETRY_DELAY..." }
                    delay(RETRY_DELAY)
                }
            }
        }

    private fun dynamicSearches(): Flow<HeightSearch> =
        flow {
            // Retained members keep their original ranges for this connection; retries use acknowledged cursors.
            val ranges = mutableMapOf<AttoAddress, AccountHeightSearch>()
            accountMonitor.membershipFlow().collect { addresses ->
                val search =
                    mutex.withLock {
                        ranges.keys.retainAll(addresses)
                        heightMap.keys.retainAll(addresses)
                        for (address in addresses - ranges.keys) {
                            val height = heightMap[address] ?: heightProvider(address).also { heightMap[address] = it }
                            ranges[address] = AccountHeightSearch(address, height)
                        }
                        HeightSearch(ranges.values.toList())
                    }
                emit(search)
            }
        }

    private fun retryingStream(addresses: Set<AttoAddress>): Flow<T> =
        flow {
            while (true) {
                val search = searchFor(addresses)
                try {
                    emitAll(stream(search).filter { it.address in addresses })
                    return@flow
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to stream monitor values. Retrying in $RETRY_DELAY..." }
                    delay(RETRY_DELAY)
                }
            }
        }

    private suspend fun searchFor(addresses: Set<AttoAddress>): HeightSearch =
        mutex.withLock {
            heightMap.keys.retainAll(addresses)

            (addresses - heightMap.keys).forEach { address -> heightMap[address] = heightProvider.invoke(address) }

            heightMap.toHeightSearch()
        }

    private fun Map<AttoAddress, AttoHeight>.toHeightSearch(): HeightSearch {
        val accounts =
            this.map {
                AccountHeightSearch(it.key, it.value)
            }

        return HeightSearch(accounts)
    }
}
