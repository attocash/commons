package cash.atto.commons.wallet

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoFuture
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoKeyIndex
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.submit
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlin.js.JsName

@JsExportForJs
class AttoWalletAsync internal constructor(
    private val wallet: AttoWallet,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    @JsName("openAccountCollection")
    fun openAccount(indexes: Collection<AttoKeyIndex>): AttoFuture<Collection<AttoWallet.Account>> =
        scope.submit { wallet.openAccount(indexes) }

    @JsName("openAccountRange")
    fun openAccount(
        fromIndex: AttoKeyIndex,
        toIndex: AttoKeyIndex,
    ): AttoFuture<Collection<AttoWallet.Account>> = scope.submit { wallet.openAccount(fromIndex, toIndex) }

    @JsName("openAccount")
    fun openAccount(index: AttoKeyIndex): AttoFuture<AttoWallet.Account> = scope.submit { wallet.openAccount(index) }

    fun closeAccount(index: AttoKeyIndex): AttoFuture<Unit> = scope.submit { wallet.closeAccount(index) }

    fun addressFlow(): Flow<Set<AttoAddress>> = wallet.addressFlow()

    @JsName("isOpenByIndex")
    fun isOpen(index: AttoKeyIndex): AttoFuture<Boolean> = scope.submit { wallet.isOpen(index) }

    @JsName("isOpenByAddress")
    fun isOpen(address: AttoAddress): AttoFuture<Boolean> = scope.submit { wallet.isOpen(address) }

    @JsName("getAccountByIndex")
    fun getAccount(index: AttoKeyIndex): AttoFuture<cash.atto.commons.AttoAccount?> = scope.submit { wallet.getAccount(index) }

    @JsName("getAccountByAddress")
    fun getAccount(address: AttoAddress): AttoFuture<cash.atto.commons.AttoAccount?> = scope.submit { wallet.getAccount(address) }

    fun getAddress(index: AttoKeyIndex): AttoFuture<AttoAddress> = scope.submit { wallet.getAddress(index) }

    fun publish(
        index: AttoKeyIndex,
        block: AttoBlock,
    ): AttoFuture<AttoTransaction> = scope.submit { wallet.publish(index, block) }

    @JsName("sendByIndex")
    fun send(
        index: AttoKeyIndex,
        receiverAddress: AttoAddress,
        amount: AttoAmount,
        timestamp: AttoInstant? = null,
    ): AttoFuture<AttoTransaction> = scope.submit { wallet.send(index, receiverAddress, amount, timestamp) }

    @JsName("sendByAddress")
    fun send(
        address: AttoAddress,
        receiverAddress: AttoAddress,
        amount: AttoAmount,
        timestamp: AttoInstant? = null,
    ): AttoFuture<AttoTransaction> = scope.submit { wallet.send(address, receiverAddress, amount, timestamp) }

    fun receive(
        receivable: AttoReceivable,
        representativeAddress: AttoAddress? = null,
        timestamp: AttoInstant? = null,
    ): AttoFuture<AttoTransaction> = scope.submit { wallet.receive(receivable, representativeAddress, timestamp) }

    fun change(
        index: AttoKeyIndex,
        representativeAddress: AttoAddress,
        timestamp: AttoInstant? = null,
    ): AttoFuture<AttoTransaction> = scope.submit { wallet.change(index, representativeAddress, timestamp) }
}
