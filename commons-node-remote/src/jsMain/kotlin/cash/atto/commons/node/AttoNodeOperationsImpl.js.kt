package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.promise
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.Promise

@OptIn(DelicateCoroutinesApi::class)
private class AttoNodeOperationsJsImpl(
    private val operations: AttoNodeOperations,
) : AttoNodeOperationsJs {
    override val network: AttoNetwork = operations.network

    override fun account(addresses: Array<AttoAddress>): Promise<Array<AttoAccount>> =
        GlobalScope.promise { operations.account(addresses.toList()).toTypedArray() }

    override fun now(): Promise<String> = GlobalScope.promise { operations.now().toString() }

    override fun publish(transaction: AttoTransaction): Promise<Unit> = GlobalScope.promise { operations.publish(transaction) }

    private fun <T> subscribeOnce(
        flowProvider: () -> kotlinx.coroutines.flow.Flow<T>,
        onUpdate: (T) -> Unit,
        onDisconnect: (String) -> Unit,
    ): JsSubscription {
        val job =
            GlobalScope.launch(SupervisorJob()) {
                var reason: String? = null
                try {
                    flowProvider().collect(onUpdate)
                    reason = "Stream completed"
                } catch (c: CancellationException) {
                    reason = c.message ?: "Cancelled"
                } catch (t: Throwable) {
                    reason = t.message ?: "Error: ${t::class.simpleName}"
                } finally {
                    onDisconnect(reason!!)
                }
            }
        return JsSubscriptionImpl(job)
    }

    override fun onAccount(
        addresses: Array<AttoAddress>,
        onUpdate: (AttoAccount) -> Unit,
        onDisconnect: (String) -> Unit,
    ): JsSubscription =
        subscribeOnce(
            { operations.accountStream(addresses.toList()) },
            onUpdate,
            onDisconnect,
        )

    override fun onReceivable(
        addresses: Array<AttoAddress>,
        onUpdate: (AttoReceivable) -> Unit,
        onDisconnect: (String) -> Unit,
    ): JsSubscription =
        subscribeOnce(
            { operations.receivableStream(addresses.toList()) },
            onUpdate,
            onDisconnect,
        )

    override fun onTransaction(
        heightSearch: HeightSearch,
        onUpdate: (AttoTransaction) -> Unit,
        onDisconnect: (String) -> Unit,
    ): JsSubscription =
        subscribeOnce(
            { operations.transactionStream(heightSearch) },
            onUpdate,
            onDisconnect,
        )

    override fun onAccountEntry(
        heightSearch: HeightSearch,
        onUpdate: (AttoAccountEntry) -> Unit,
        onDisconnect: (String) -> Unit,
    ): JsSubscription =
        subscribeOnce(
            { operations.accountEntryStream(heightSearch) },
            onUpdate,
            onDisconnect,
        )
}

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("createCustomAttoNodeClient")
fun createCustomAttoNodeClient(
    network: AttoNetwork,
    baseUrl: String,
): AttoNodeOperationsJs {
    val operations = AttoNodeOperations.custom(network, baseUrl) { emptyMap() }
    return AttoNodeOperationsJsImpl(operations)
}
