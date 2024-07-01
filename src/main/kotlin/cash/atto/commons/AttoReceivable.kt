@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class AttoReceivable(
    val hash: AttoHash,
    val version: AttoVersion,
    val algorithm: AttoAlgorithm,
    val receiverAlgorithm: AttoAlgorithm,
    val receiverPublicKey: AttoPublicKey,
    val amount: AttoAmount,
)

fun AttoSendBlock.toReceivable(): AttoReceivable =
    AttoReceivable(
        hash = this.hash,
        version = this.version,
        algorithm = this.algorithm,
        receiverAlgorithm = this.receiverAlgorithm,
        receiverPublicKey = this.receiverPublicKey,
        amount = this.amount,
    )
