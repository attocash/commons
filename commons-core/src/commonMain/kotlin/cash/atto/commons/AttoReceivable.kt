@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class AttoReceivable(
    val hash: AttoHash,
    val version: AttoVersion,
    val algorithm: AttoAlgorithm,
    val publicKey: AttoPublicKey,
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
