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
            timestamp: Instant = Clock.System.now(),
        ): AttoOpenBlock {
            return AttoOpenBlock(
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
        }
    }

    fun send(
        receiverAlgorithm: AttoAlgorithm,
        receiverPublicKey: AttoPublicKey,
        amount: AttoAmount,
        timestamp: Instant = Clock.System.now(),
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
            timestamp = timestamp,
            previous = lastTransactionHash,
            receiverAlgorithm = receiverAlgorithm,
            receiverPublicKey = receiverPublicKey,
            amount = amount,
        )
    }

    fun receive(
        receivable: AttoReceivable,
        timestamp: Instant = Clock.System.now(),
    ): AttoReceiveBlock {
        require(timestamp > receivable.timestamp) { "Timestamp can't be before receivable timestamp" }

        return AttoReceiveBlock(
            network = network,
            version = version.max(receivable.version),
            algorithm = algorithm,
            publicKey = publicKey,
            height = height + 1U,
            balance = balance.plus(receivable.amount),
            timestamp = timestamp,
            previous = lastTransactionHash,
            sendHashAlgorithm = receivable.algorithm,
            sendHash = receivable.hash,
        )
    }

    fun change(
        representativeAlgorithm: AttoAlgorithm,
        representativePublicKey: AttoPublicKey,
        timestamp: Instant = Clock.System.now(),
    ): AttoChangeBlock =
        AttoChangeBlock(
            network = network,
            version = version,
            algorithm = algorithm,
            publicKey = publicKey,
            height = height + 1U,
            balance = balance,
            timestamp = timestamp,
            previous = lastTransactionHash,
            representativeAlgorithm = representativeAlgorithm,
            representativePublicKey = representativePublicKey,
        )
}
