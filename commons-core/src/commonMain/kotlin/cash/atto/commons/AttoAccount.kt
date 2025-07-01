package cash.atto.commons

import cash.atto.commons.serialiazer.InstantMillisSerializer
import cash.atto.commons.utils.JsExportForJs
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExportForJs
data class AttoAccount(
    val publicKey: AttoPublicKey,
    val network: AttoNetwork,
    val version: AttoVersion,
    val algorithm: AttoAlgorithm,
    override val height: AttoHeight,
    val balance: AttoAmount,
    val lastTransactionHash: AttoHash,
    @Serializable(with = InstantMillisSerializer::class)
    val lastTransactionTimestamp: Instant,
    val representativeAlgorithm: AttoAlgorithm,
    val representativePublicKey: AttoPublicKey,
) : HeightSupport {
    val address by lazy { AttoAddress(algorithm, publicKey) }
    val representativeAddress by lazy { AttoAddress(representativeAlgorithm, representativePublicKey) }

    companion object {
        @JsExport.Ignore
        fun open(
            representativeAlgorithm: AttoAlgorithm,
            representativePublicKey: AttoPublicKey,
            receivable: AttoReceivable,
            network: AttoNetwork,
            timestamp: Instant = Clock.System.now(),
        ): Pair<AttoOpenBlock, AttoAccount> {
            val block =
                AttoOpenBlock(
                    network = network,
                    version = receivable.version,
                    algorithm = receivable.receiverAlgorithm,
                    publicKey = receivable.receiverPublicKey,
                    balance = receivable.amount,
                    timestamp = timestamp,
                    sendHashAlgorithm = receivable.algorithm,
                    sendHash = receivable.hash,
                    representativeAlgorithm = representativeAlgorithm,
                    representativePublicKey = representativePublicKey,
                )
            val account =
                AttoAccount(
                    publicKey = receivable.receiverPublicKey,
                    network = network,
                    version = receivable.version,
                    algorithm = receivable.receiverAlgorithm,
                    height = block.height,
                    balance = receivable.amount,
                    lastTransactionHash = block.hash,
                    lastTransactionTimestamp = timestamp,
                    representativeAlgorithm = representativeAlgorithm,
                    representativePublicKey = representativePublicKey,
                )
            return Pair(block, account)
        }
    }

    @JsExport.Ignore
    fun send(
        receiverAlgorithm: AttoAlgorithm,
        receiverPublicKey: AttoPublicKey,
        amount: AttoAmount,
        timestamp: Instant = Clock.System.now(),
    ): Pair<AttoSendBlock, AttoAccount> {
        if (receiverPublicKey == publicKey) {
            throw IllegalArgumentException("You can't send money to yourself")
        }
        val newBalance = balance.minus(amount)
        val newHeight = height + 1U.toAttoHeight()
        val block =
            AttoSendBlock(
                network = network,
                version = version,
                algorithm = algorithm,
                publicKey = publicKey,
                height = newHeight,
                balance = newBalance,
                timestamp = timestamp,
                previous = lastTransactionHash,
                receiverAlgorithm = receiverAlgorithm,
                receiverPublicKey = receiverPublicKey,
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
        timestamp: Instant = Clock.System.now(),
    ): Pair<AttoReceiveBlock, AttoAccount> {
        require(timestamp > receivable.timestamp) { "Timestamp can't be before receivable timestamp" }

        val newBalance = balance.plus(receivable.amount)
        val newHeight = height + 1U.toAttoHeight()
        val newVersion = version.max(receivable.version)
        val block =
            AttoReceiveBlock(
                network = network,
                version = newVersion,
                algorithm = algorithm,
                publicKey = publicKey,
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
        representativeAlgorithm: AttoAlgorithm,
        representativePublicKey: AttoPublicKey,
        timestamp: Instant = Clock.System.now(),
    ): Pair<AttoChangeBlock, AttoAccount> {
        val newHeight = height + 1U.toAttoHeight()
        val block =
            AttoChangeBlock(
                network = network,
                version = version,
                algorithm = algorithm,
                publicKey = publicKey,
                height = newHeight,
                balance = balance,
                timestamp = timestamp,
                previous = lastTransactionHash,
                representativeAlgorithm = representativeAlgorithm,
                representativePublicKey = representativePublicKey,
            )
        val updatedAccount =
            copy(
                height = newHeight,
                lastTransactionHash = block.hash,
                lastTransactionTimestamp = timestamp,
                representativeAlgorithm = representativeAlgorithm,
                representativePublicKey = representativePublicKey,
            )
        return Pair(block, updatedAccount)
    }
}
