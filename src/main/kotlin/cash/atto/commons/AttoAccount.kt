package cash.atto.commons

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable


@Serializable
data class AttoAccount(
    @Contextual val publicKey: AttoPublicKey,
    val version: UShort,
    val algorithm: AttoAlgorithm,
    override val height: ULong,
    val balance: AttoAmount,
    @Contextual val lastTransactionHash: AttoHash,
    val lastTransactionTimestamp: Instant,
    @Contextual val representative: AttoPublicKey,
) : HeightSupport {

    companion object {
        fun open(
            representative: AttoPublicKey,
            receivable: AttoReceivable,
        ): AttoOpenBlock {
            return AttoOpenBlock(
                version = receivable.version,
                algorithm = receivable.receiverAlgorithm,
                publicKey = receivable.receiverPublicKey,
                balance = receivable.amount,
                timestamp = Clock.System.now(),
                sendHashAlgorithm = receivable.algorithm,
                sendHash = receivable.hash,
                representative = representative,
            )
        }
    }

    fun send(
        receiverAlgorithm: AttoAlgorithm,
        receiverPublicKey: AttoPublicKey,
        amount: AttoAmount
    ): AttoSendBlock {
        if (receiverPublicKey == publicKey) {
            throw IllegalArgumentException("You can't send money to yourself");
        }
        return AttoSendBlock(
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

    fun receive(receivable: AttoReceivable): AttoReceiveBlock {
        return AttoReceiveBlock(
            version = max(version, receivable.version),
            algorithm = algorithm,
            publicKey = publicKey,
            height = height + 1U,
            balance = balance.plus(receivable.amount),
            timestamp = Clock.System.now(),
            previous = lastTransactionHash,
            sendHashAlgorithm = receivable.algorithm,
            sendHash = receivable.hash,
        )
    }

    fun change(representative: AttoPublicKey): AttoChangeBlock {
        return AttoChangeBlock(
            version = version,
            algorithm = algorithm,
            publicKey = publicKey,
            height = height + 1U,
            balance = balance,
            timestamp = Clock.System.now(),
            previous = lastTransactionHash,
            representative = representative,
        )
    }

    private fun max(n1: UShort, n2: UShort): UShort {
        if (n1 > n2) {
            return n1
        }
        return n2
    }
}

