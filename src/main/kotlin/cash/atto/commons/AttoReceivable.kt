@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class AttoReceivable(
    @ProtoNumber(0)
    @Contextual
    val hash: AttoHash,
    @ProtoNumber(1)
    val version: UShort,
    @ProtoNumber(2)
    val algorithm: AttoAlgorithm,
    @ProtoNumber(3)
    val receiverAlgorithm: AttoAlgorithm,
    @ProtoNumber(4)
    @Contextual
    val receiverPublicKey: AttoPublicKey,
    @ProtoNumber(5)
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