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
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
actual class AttoWalletAsync internal actual constructor(
    internal actual val wallet: AttoWallet,
    dispatcher: CoroutineDispatcher,
) {
    @JsName("openAccountCollection")
    suspend fun openAccount(indexes: Collection<AttoKeyIndex>): Collection<AttoWalletAccount> = wallet.openAccount(indexes)

    @JsName("openAccountRange")
    suspend fun openAccount(
        fromIndex: AttoKeyIndex,
        toIndex: AttoKeyIndex,
    ): Collection<AttoWalletAccount> = wallet.openAccount(fromIndex, toIndex)

    @JsName("openAccount")
    suspend fun openAccount(index: AttoKeyIndex): AttoWalletAccount = wallet.openAccount(index)

    suspend fun closeAccount(index: AttoKeyIndex): Unit = wallet.closeAccount(index)

    @JsName("isOpenByIndex")
    suspend fun isOpen(index: AttoKeyIndex): Boolean = wallet.isOpen(index)

    @JsName("isOpenByAddress")
    suspend fun isOpen(address: AttoAddress): Boolean = wallet.isOpen(address)

    @JsName("getAccountByIndex")
    suspend fun getAccount(index: AttoKeyIndex): cash.atto.commons.AttoAccount? = wallet.getAccount(index)

    @JsName("getAccountByAddress")
    suspend fun getAccount(address: AttoAddress): cash.atto.commons.AttoAccount? = wallet.getAccount(address)

    suspend fun getAddress(index: AttoKeyIndex): AttoAddress = wallet.getAddress(index)

    suspend fun publish(
        index: AttoKeyIndex,
        block: AttoBlock,
    ): AttoTransaction = wallet.publish(index, block)

    @JsName("sendByIndex")
    suspend fun send(
        index: AttoKeyIndex,
        receiverAddress: AttoAddress,
        amount: AttoAmount,
        timestamp: AttoInstant? = null,
    ): AttoTransaction = wallet.send(index, receiverAddress, amount, timestamp)

    @JsName("sendByAddress")
    suspend fun send(
        address: AttoAddress,
        receiverAddress: AttoAddress,
        amount: AttoAmount,
        timestamp: AttoInstant? = null,
    ): AttoTransaction = wallet.send(address, receiverAddress, amount, timestamp)

    suspend fun receive(
        receivable: AttoReceivable,
        representativeAddress: AttoAddress? = null,
        timestamp: AttoInstant? = null,
    ): AttoTransaction = wallet.receive(receivable, representativeAddress, timestamp)

    suspend fun change(
        index: AttoKeyIndex,
        representativeAddress: AttoAddress,
        timestamp: AttoInstant? = null,
    ): AttoTransaction = wallet.change(index, representativeAddress, timestamp)
}
