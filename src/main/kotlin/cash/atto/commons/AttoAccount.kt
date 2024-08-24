@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import cash.atto.commons.serialiazers.InstantMillisSerializer
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
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
    companion object {
        fun open(
            representativeAlgorithm: AttoAlgorithm,
            representativePublicKey: AttoPublicKey,
            receivable: AttoReceivable,
            network: AttoNetwork,
        ): AttoOpenBlock {
            return AttoOpenBlock(
                network = network,
                version = receivable.version,
                algorithm = receivable.receiverAlgorithm,
                publicKey = receivable.receiverPublicKey,
                balance = receivable.amount,
                timestamp = Clock.System.now(),
                sendHashAlgorithm = receivable.algorithm,
                sendHash = receivable.hash,
                representativeAlgorithm = representativeAlgorithm,
                representativePublicKey = representativePublicKey,
            )
        }
    }

    fun send(
        receiverAlgorithm: AttoAlgorithm,
        receiverPublicKey: AttoPublicKey,
        amount: AttoAmount,
    ): AttoSendBlock {
        if (receiverPublicKey == publicKey) {
            throw IllegalArgumentException("You can't send money to yourself")
        }
        return AttoSendBlock(
            network = network,
            version = version,
            algorithm = algorithm,
            publicKey = publicKey,
            height = height + 1U,
            balance = balance.minus(amount),
            timestamp = Clock.System.now(),
            previous = lastTransactionHash,
            receiverAlgorithm = receiverAlgorithm,
            receiverPublicKey = receiverPublicKey,
            amount = amount,
        )
    }

    fun receive(receivable: AttoReceivable): AttoReceiveBlock =
        AttoReceiveBlock(
            network = network,
            version = version.max(receivable.version),
            algorithm = algorithm,
            publicKey = publicKey,
            height = height + 1U,
            balance = balance.plus(receivable.amount),
            timestamp = Clock.System.now(),
            previous = lastTransactionHash,
            sendHashAlgorithm = receivable.algorithm,
            sendHash = receivable.hash,
        )

    fun change(
        representativeAlgorithm: AttoAlgorithm,
        representativePublicKey: AttoPublicKey,
    ): AttoChangeBlock =
        AttoChangeBlock(
            network = network,
            version = version,
            algorithm = algorithm,
            publicKey = publicKey,
            height = height + 1U,
            balance = balance,
            timestamp = Clock.System.now(),
            previous = lastTransactionHash,
            representativeAlgorithm = representativeAlgorithm,
            representativePublicKey = representativePublicKey,
        )
}
