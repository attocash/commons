package cash.atto.commons

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class AttoReceivable(
    @Contextual
    val hash: AttoHash,
    val version: UShort,
    val algorithm: AttoAlgorithm,
    val receiverPublicKeyAlgorithm: AttoAlgorithm,
    @Contextual
    val receiverPublicKey: AttoPublicKey,
    val amount: AttoAmount
)

fun AttoSendBlock.toReceivable(): AttoReceivable {
    return AttoReceivable(
        hash = this.hash,
        version = this.version,
        algorithm = this.algorithm,
        receiverPublicKeyAlgorithm = this.receiverPublicKeyAlgorithm,
        receiverPublicKey = this.receiverPublicKey,
        amount = this.amount
    )
}