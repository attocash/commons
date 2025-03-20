package cash.atto.commons

import cash.atto.commons.serialiazer.InstantMillisSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AttoReceivable(
    val hash: AttoHash,
    val version: AttoVersion,
    val algorithm: AttoAlgorithm,
    val publicKey: AttoPublicKey,
    @Serializable(with = InstantMillisSerializer::class)
    val timestamp: Instant,
    val receiverAlgorithm: AttoAlgorithm,
    val receiverPublicKey: AttoPublicKey,
    val amount: AttoAmount,
)

fun AttoSendBlock.toReceivable(): AttoReceivable =
    AttoReceivable(
        hash = this.hash,
        version = this.version,
        algorithm = this.algorithm,
        publicKey = this.publicKey,
        timestamp = this.timestamp,
        receiverAlgorithm = this.receiverAlgorithm,
        receiverPublicKey = this.receiverPublicKey,
        amount = this.amount,
    )
