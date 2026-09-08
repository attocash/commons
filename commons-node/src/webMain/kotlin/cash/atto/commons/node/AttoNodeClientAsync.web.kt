package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoJob
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoVoterWeight
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
actual class AttoNodeClientAsync
    @JsExport.Ignore
    actual constructor(
        actual val client: AttoNodeClient,
        dispatcher: CoroutineDispatcher,
    ) : AutoCloseable {
        private val scope = CoroutineScope(dispatcher + SupervisorJob())

        @JsName("accountByPublicKey")
        suspend fun account(publicKey: AttoPublicKey): AttoAccount? = client.account(publicKey)

        @JsName("accountByAddresses")
        suspend fun account(addresses: Array<AttoAddress>): Array<AttoAccount> = client.account(addresses.toList()).toTypedArray()

        @JsName("onAccountAll")
        fun onAccount(
            onAccount: (AttoAccount) -> Any,
            onCancel: (Throwable?) -> Any,
        ): AttoJob =
            scope.consumeStream(
                stream = client.accountStream(),
                onEach = { onAccount(it) },
                onCancel = { onCancel.invoke(it) },
            )

        @JsName("onAccountByPublicKey")
        fun onAccount(
            publicKey: AttoPublicKey,
            onAccount: (AttoAccount) -> Any,
            onCancel: (Throwable?) -> Any,
        ): AttoJob =
            scope.consumeStream(
                stream = client.accountStream(publicKey),
                onEach = { onAccount(it) },
                onCancel = { onCancel.invoke(it) },
            )

        @JsName("onAccountByAddresses")
        fun onAccount(
            addresses: Array<AttoAddress>,
            onAccount: (AttoAccount) -> Unit,
            onCancel: (Throwable?) -> Any,
        ): AttoJob =
            scope.consumeStream(
                stream = client.accountStream(addresses.toList()),
                onEach = { onAccount(it) },
                onCancel = { onCancel.invoke(it) },
            )

        @JsName("onReceivableByPublicKey")
        fun onReceivable(
            publicKey: AttoPublicKey,
            minAmount: AttoAmount = AttoAmount(1U),
            onReceivable: (AttoReceivable) -> Any,
            onCancel: (Throwable?) -> Any,
        ): AttoJob =
            scope.consumeStream(
                stream = client.receivableStream(publicKey, minAmount),
                onEach = { onReceivable.invoke(it) },
                onCancel = { onCancel.invoke(it) },
            )

        @JsName("onReceivableByAddresses")
        fun onReceivable(
            addresses: Array<AttoAddress>,
            minAmount: AttoAmount = AttoAmount(1U),
            onReceivable: (AttoReceivable) -> Any,
            onCancel: (Throwable?) -> Any,
        ): AttoJob =
            scope.consumeStream(
                stream = client.receivableStream(addresses.toList(), minAmount),
                onEach = { onReceivable.invoke(it) },
                onCancel = { onCancel.invoke(it) },
            )

        suspend fun accountEntry(hash: AttoHash): AttoAccountEntry = client.accountEntry(hash)

        @JsName("onAccountEntryAll")
        fun onAccountEntry(
            onAccountEntry: (AttoAccountEntry) -> Any,
            onCancel: (Throwable?) -> Any,
        ): AttoJob =
            scope.consumeStream(
                stream = client.accountEntryStream(),
                onEach = { onAccountEntry.invoke(it) },
                onCancel = { onCancel.invoke(it) },
            )

        @JsName("onAccountEntryByHash")
        fun onAccountEntry(
            hash: AttoHash,
            onAccountEntry: (AttoAccountEntry) -> Any,
            onCancel: (Throwable?) -> Any,
        ): AttoJob =
            scope.consumeStream(
                stream = client.accountEntryStream(hash),
                onEach = { onAccountEntry.invoke(it) },
                onCancel = { onCancel.invoke(it) },
            )

        @JsName("onAccountEntryByPublicKey")
        fun onAccountEntry(
            publicKey: AttoPublicKey,
            fromHeight: AttoHeight = AttoHeight(1UL),
            toHeight: AttoHeight? = null,
            onAccountEntry: (AttoAccountEntry) -> Any,
            onCancel: (Throwable?) -> Any,
        ): AttoJob =
            scope.consumeStream(
                stream = client.accountEntryStream(publicKey, fromHeight, toHeight),
                onEach = { onAccountEntry.invoke(it) },
                onCancel = { onCancel.invoke(it) },
            )

        @JsName("onAccountEntryByHeightSearch")
        fun onAccountEntry(
            heightSearch: HeightSearch,
            onAccountEntry: (AttoAccountEntry) -> Any,
            onCancel: (Throwable?) -> Any,
        ): AttoJob =
            scope.consumeStream(
                stream = client.accountEntryStream(heightSearch),
                onEach = { onAccountEntry.invoke(it) },
                onCancel = { onCancel.invoke(it) },
            )

        suspend fun transaction(hash: AttoHash): AttoTransaction = client.transaction(hash)

        @JsName("onTransactionAll")
        fun onTransaction(
            onTransaction: (AttoTransaction) -> Any,
            onCancel: (Throwable?) -> Any,
        ): AttoJob =
            scope.consumeStream(
                stream = client.transactionStream(),
                onEach = { onTransaction.invoke(it) },
                onCancel = { onCancel.invoke(it) },
            )

        @JsName("onTransactionByHash")
        fun onTransaction(
            hash: AttoHash,
            onTransaction: (AttoTransaction) -> Any,
            onCancel: (Throwable?) -> Any,
        ): AttoJob =
            scope.consumeStream(
                stream = client.transactionStream(hash),
                onEach = { onTransaction.invoke(it) },
                onCancel = { onCancel.invoke(it) },
            )

        @JsName("onTransactionByPublicKey")
        fun onTransaction(
            publicKey: AttoPublicKey,
            fromHeight: AttoHeight = AttoHeight(1UL),
            toHeight: AttoHeight? = null,
            onTransaction: (AttoTransaction) -> Any,
            onCancel: (Throwable?) -> Any,
        ): AttoJob =
            scope.consumeStream(
                stream = client.transactionStream(publicKey, fromHeight, toHeight),
                onEach = { onTransaction.invoke(it) },
                onCancel = { onCancel.invoke(it) },
            )

        @JsName("onTransactionByHeightSearch")
        fun onTransaction(
            heightSearch: HeightSearch,
            onTransaction: (AttoTransaction) -> Any,
            onCancel: (Throwable?) -> Any,
        ): AttoJob =
            scope.consumeStream(
                stream = client.transactionStream(heightSearch),
                onEach = { onTransaction.invoke(it) },
                onCancel = { onCancel.invoke(it) },
            )

        suspend fun now(): AttoInstant = client.now()

        suspend fun publish(transaction: AttoTransaction): Unit = client.publish(transaction)

        suspend fun voterWeight(address: AttoAddress): AttoVoterWeight = client.voterWeight(address)

        actual override fun close() {
            scope.cancel()
        }
    }

fun AttoNodeClient.toAsync(): AttoNodeClientAsync = AttoNodeClientAsync(this, Dispatchers.Default)
