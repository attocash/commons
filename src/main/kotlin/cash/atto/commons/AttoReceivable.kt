package cash.atto.commons

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class AttoReceivable(
    @Contextual
    val hash: AttoHash,
    val version: UShort,
    val algorithm: AttoAlgorithm,
    val receiverAlgorithm: AttoAlgorithm,
    @Contextual
    val receiverPublicKey: AttoPublicKey,
    val amount: AttoAmount
)

fun AttoSendBlock.toReceivable(): AttoReceivable {
    return AttoReceivable(
        hash = this.hash,
        version = this.version,
        algorithm = this.algorithm,
        receiverAlgorithm = this.receiverAlgorithm,
        receiverPublicKey = this.receiverPublicKey,
        amount = this.amount
    )
}