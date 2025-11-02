package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoReceivable
import cash.atto.commons.node.AttoNodeClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AttoAccountMonitor internal constructor(
    internal val client: AttoNodeClient,
) {
    private val state = State()

    suspend fun monitor(addresses: Collection<AttoAddress>) {
        state.add(addresses)
    }

    suspend fun monitor(address: AttoAddress) {
        state.add(listOf(address))
    }

    suspend fun isMonitored(address: AttoAddress): Boolean = state.isMonitored(address)

    suspend fun stopMonitoring(address: AttoAddress) {
        state.remove(address)
    }

    suspend fun getAccounts(): Collection<AttoAccount> = client.account(state.getAddresses())

    suspend fun getAccount(address: AttoAddress): AttoAccount? {
        require(state.getAddresses().contains(address)) { "Address $address not found in the monitor" }
        return client.account(address.publicKey)
    }

    fun membershipFlow(): Flow<Set<AttoAddress>> = state.membershipFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun receivableStream(minAmount: AttoAmount = AttoAmount.MIN): Flow<AttoReceivable> {
        return membershipFlow()
            .flatMapLatest { addresses ->
                if (addresses.isEmpty()) {
                    return@flatMapLatest emptyFlow()
                }
                return@flatMapLatest client.receivableStream(addresses, minAmount)
            }
    }

    private class State {
        private val mutex = Mutex()
        private val addresses = mutableSetOf<AttoAddress>()
        private val addressesFlow = MutableStateFlow<Set<AttoAddress>>(emptySet())

        suspend fun isMonitored(address: AttoAddress): Boolean {
            mutex.withLock {
                return addresses.contains(address)
            }
        }

        suspend fun getAddresses(): Set<AttoAddress> =
            mutex.withLock {
                addresses.toSet()
            }

        suspend fun add(newAddresses: Collection<AttoAddress>) {
            mutex.withLock {
                addresses += newAddresses
                addressesFlow.update { _ -> addresses.toSet() }
            }
        }

        suspend fun remove(address: AttoAddress) {
            mutex.withLock {
                addresses -= address
                addressesFlow.update { _ -> addresses.toSet() }
            }
        }

        fun membershipFlow(): Flow<Set<AttoAddress>> = addressesFlow.asStateFlow()
    }
}

fun AttoNodeClient.createAccountMonitor(): AttoAccountMonitor = AttoAccountMonitor(this)
