package cash.atto.commons

import kotlinx.serialization.Serializable

@Serializable
data class AttoAccountEntry(
    val hash: AttoHash,
    val algorithm: AttoAlgorithm,
    val publicKey: AttoPublicKey,
    override val height: AttoHeight,
    val blockType: AttoBlockType,
    val subjectAlgorithm: AttoAlgorithm,
    val subjectPublicKey: AttoPublicKey,
    val previousBalance: AttoAmount,
    val balance: AttoAmount
) : HeightSupport
