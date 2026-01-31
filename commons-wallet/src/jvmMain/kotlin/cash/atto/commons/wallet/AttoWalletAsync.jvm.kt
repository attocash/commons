package cash.atto.commons.wallet

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoKeyIndex
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

@JsExportForJs
actual class AttoWalletAsync internal actual constructor(
    internal actual val wallet: AttoWallet,
    dispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun openAccount(indexes: Collection<AttoKeyIndex>): CompletableFuture<Collection<AttoWalletAccount>> =
        scope.future { wallet.openAccount(indexes) }

    fun openAccount(
        fromIndex: AttoKeyIndex,
        toIndex: AttoKeyIndex,
    ): CompletableFuture<Collection<AttoWalletAccount>> = scope.future { wallet.openAccount(fromIndex, toIndex) }

    fun openAccount(index: AttoKeyIndex): CompletableFuture<AttoWalletAccount> = scope.future { wallet.openAccount(index) }

    fun closeAccount(index: AttoKeyIndex): CompletableFuture<Unit> = scope.future { wallet.closeAccount(index) }

    fun isOpen(index: AttoKeyIndex): CompletableFuture<Boolean> = scope.future { wallet.isOpen(index) }

    fun isOpen(address: AttoAddress): CompletableFuture<Boolean> = scope.future { wallet.isOpen(address) }

    fun getAccount(index: AttoKeyIndex): CompletableFuture<cash.atto.commons.AttoAccount?> = scope.future { wallet.getAccount(index) }

    fun getAccount(address: AttoAddress): CompletableFuture<cash.atto.commons.AttoAccount?> = scope.future { wallet.getAccount(address) }

    fun getAddress(index: AttoKeyIndex): CompletableFuture<AttoAddress> = scope.future { wallet.getAddress(index) }

    fun publish(
        index: AttoKeyIndex,
        block: AttoBlock,
    ): CompletableFuture<AttoTransaction> = scope.future { wallet.publish(index, block) }

    @JvmOverloads
    fun send(
        index: AttoKeyIndex,
        receiverAddress: AttoAddress,
        amount: AttoAmount,
        timestamp: AttoInstant? = null,
    ): CompletableFuture<AttoTransaction> = scope.future { wallet.send(index, receiverAddress, amount, timestamp) }

    @JvmOverloads
    fun send(
        address: AttoAddress,
        receiverAddress: AttoAddress,
        amount: AttoAmount,
        timestamp: AttoInstant? = null,
    ): CompletableFuture<AttoTransaction> = scope.future { wallet.send(address, receiverAddress, amount, timestamp) }

    @JvmOverloads
    fun receive(
        receivable: AttoReceivable,
        representativeAddress: AttoAddress? = null,
        timestamp: AttoInstant? = null,
    ): CompletableFuture<AttoTransaction> = scope.future { wallet.receive(receivable, representativeAddress, timestamp) }

    @JvmOverloads
    fun change(
        index: AttoKeyIndex,
        representativeAddress: AttoAddress,
        timestamp: AttoInstant? = null,
    ): CompletableFuture<AttoTransaction> = scope.future { wallet.change(index, representativeAddress, timestamp) }
}
