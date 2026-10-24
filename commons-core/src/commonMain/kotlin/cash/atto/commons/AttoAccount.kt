@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsStatic

@Serializable(with = AttoAccountSerializer::class)
@JsExportForJs
data class AttoAccount(
    override val address: AttoAddress,
    val network: AttoNetwork,
    val version: AttoVersion,
    override val height: AttoHeight,
    val balance: AttoAmount,
    val lastTransactionHash: AttoHash,
    val lastTransactionTimestamp: AttoInstant,
    val representativeAddress: AttoAddress,
) : HeightSupport,
    AddressSupport {
    val algorithm: AttoAlgorithm
        get() = address.algorithm

    val publicKey: AttoPublicKey
        get() = address.publicKey

    val representativeAlgorithm: AttoAlgorithm
        get() = representativeAddress.algorithm

    val representativePublicKey: AttoPublicKey
        get() = representativeAddress.publicKey

    companion object {
        @JsStatic
        fun fromJson(value: String): AttoAccount = attoJson.decodeFromString<AttoAccount>(value)

        @JsExport.Ignore
        fun open(
            representativeAddress: AttoAddress,
            receivable: AttoReceivable,
            timestamp: AttoInstant = AttoInstant.now(),
        ): Pair<AttoOpenBlock, AttoAccount> {
            val block =
                AttoOpenBlock(
                    network = receivable.network,
                    version = receivable.version,
                    address = receivable.receiverAddress,
                    balance = receivable.amount,
                    timestamp = timestamp,
                    sendHashAlgorithm = receivable.algorithm,
                    sendHash = receivable.hash,
                    representativeAddress = representativeAddress,
                )
            val account =
                AttoAccount(
                    address = receivable.receiverAddress,
                    network = receivable.network,
                    version = receivable.version,
                    height = block.height,
                    balance = receivable.amount,
                    lastTransactionHash = block.hash,
                    lastTransactionTimestamp = timestamp,
                    representativeAddress = representativeAddress,
                )
            return Pair(block, account)
        }
    }

    @JsExport.Ignore
    fun send(
        receiverAddress: AttoAddress,
        amount: AttoAmount,
        timestamp: AttoInstant = AttoInstant.now(),
    ): Pair<AttoSendBlock, AttoAccount> {
        if (receiverAddress.publicKey == publicKey) {
            throw IllegalArgumentException("You can't send money to yourself")
        }
        val newBalance = balance.minus(amount)
        val newHeight = height + 1U.toAttoHeight()
        val block =
            AttoSendBlock(
                network = network,
                version = version,
                address = address,
                height = newHeight,
                balance = newBalance,
                timestamp = timestamp,
                previous = lastTransactionHash,
                receiverAddress = receiverAddress,
                amount = amount,
            )
        val updatedAccount =
            copy(
                height = newHeight,
                balance = newBalance,
                lastTransactionHash = block.hash,
                lastTransactionTimestamp = timestamp,
            )
        return Pair(block, updatedAccount)
    }

    @JsExport.Ignore
    fun receive(
        receivable: AttoReceivable,
        timestamp: AttoInstant = AttoInstant.now(),
    ): Pair<AttoReceiveBlock, AttoAccount> {
        require(timestamp > receivable.timestamp) { "Timestamp can't be before receivable timestamp" }

        val newBalance = balance.plus(receivable.amount)
        val newHeight = height + 1U.toAttoHeight()
        val newVersion = version.max(receivable.version)
        val block =
            AttoReceiveBlock(
                network = network,
                version = newVersion,
                address = address,
                height = newHeight,
                balance = newBalance,
                timestamp = timestamp,
                previous = lastTransactionHash,
                sendHashAlgorithm = receivable.algorithm,
                sendHash = receivable.hash,
            )
        val updatedAccount =
            copy(
                version = newVersion,
                height = newHeight,
                balance = newBalance,
                lastTransactionHash = block.hash,
                lastTransactionTimestamp = timestamp,
            )
        return Pair(block, updatedAccount)
    }

    @JsExport.Ignore
    fun change(
        representativeAddress: AttoAddress,
        timestamp: AttoInstant = AttoInstant.now(),
    ): Pair<AttoChangeBlock, AttoAccount> {
        val newHeight = height + 1U.toAttoHeight()
        val block =
            AttoChangeBlock(
                network = network,
                version = version,
                address = address,
                height = newHeight,
                balance = balance,
                timestamp = timestamp,
                previous = lastTransactionHash,
                representativeAddress = representativeAddress,
            )
        val updatedAccount =
            copy(
                height = newHeight,
                lastTransactionHash = block.hash,
                lastTransactionTimestamp = timestamp,
                representativeAddress = representativeAddress,
            )
        return Pair(block, updatedAccount)
    }

    fun toJson(): String = attoJson.encodeToString(this)
}
