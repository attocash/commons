package cash.atto.commons

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable


@Serializable
data class AttoAccount(
    val publicKey: AttoPublicKey,
    var version: UShort,
    var algorithm: AttoAlgorithm,
    override var height: ULong,
    var balance: AttoAmount,
    var lastTransactionHash: AttoHash,
    var lastTransactionTimestamp: Instant,
    var representative: AttoPublicKey,
) : HeightSupport {

    companion object {
        fun open(
            representative: AttoPublicKey,
            sendBlock: AttoSendBlock
        ): AttoOpenBlock {
            return AttoOpenBlock(
                version = sendBlock.version,
                algorithm = sendBlock.receiverPublicKeyAlgorithm,
                publicKey = sendBlock.receiverPublicKey,
                balance = sendBlock.amount,
                timestamp = Clock.System.now(),
                sendHashAlgorithm = sendBlock.algorithm,
                sendHash = sendBlock.hash,
                representative = representative,
            )
        }
    }

    fun send(
        receiverPublicKeyAlgorithm: AttoAlgorithm,
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
            receiverPublicKeyAlgorithm = receiverPublicKeyAlgorithm,
            receiverPublicKey = receiverPublicKey,
            amount = amount,
        )
    }

    fun receive(sendBlock: AttoSendBlock): AttoReceiveBlock {
        return AttoReceiveBlock(
            version = max(version, sendBlock.version),
            algorithm = algorithm,
            publicKey = publicKey,
            height = height + 1U,
            balance = balance.plus(sendBlock.amount),
            timestamp = Clock.System.now(),
            previous = lastTransactionHash,
            sendHashAlgorithm = sendBlock.algorithm,
            sendHash = sendBlock.hash,
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

