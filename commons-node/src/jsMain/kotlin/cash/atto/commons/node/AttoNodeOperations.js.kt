package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import kotlinx.coroutines.Job
import kotlin.js.Promise

@OptIn(ExperimentalJsExport::class)
@JsExport
interface JsSubscription {
    fun cancel()
}

class JsSubscriptionImpl(
    private val job: Job,
) : JsSubscription {
    override fun cancel() {
        job.cancel()
    }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
interface AttoNodeOperationsJs {
    val network: AttoNetwork

    fun account(addresses: Array<AttoAddress>): Promise<Array<AttoAccount>>

    fun accountEntry(hash: AttoHash): Promise<AttoAccountEntry>

    fun transaction(hash: AttoHash): Promise<AttoTransaction>

    fun now(): Promise<String>

    fun publish(transaction: AttoTransaction): Promise<Unit>

    fun onAccount(
        addresses: Array<AttoAddress>,
        onUpdate: (AttoAccount) -> Unit,
        onDisconnect: (String) -> Unit = {},
    ): JsSubscription

    fun onReceivable(
        addresses: Array<AttoAddress>,
        onUpdate: (AttoReceivable) -> Unit,
        onDisconnect: (String) -> Unit = {},
    ): JsSubscription

    fun onTransaction(
        heightSearch: HeightSearch,
        onUpdate: (AttoTransaction) -> Unit,
        onDisconnect: (String) -> Unit = {},
    ): JsSubscription

    fun onAccountEntry(
        heightSearch: HeightSearch,
        onUpdate: (AttoAccountEntry) -> Unit,
        onDisconnect: (String) -> Unit = {},
    ): JsSubscription
}
