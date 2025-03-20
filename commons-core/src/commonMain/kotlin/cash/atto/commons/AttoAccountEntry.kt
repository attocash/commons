package cash.atto.commons

import cash.atto.commons.serialiazer.InstantMillisSerializer
import kotlinx.datetime.Instant
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
    val balance: AttoAmount,
    @Serializable(with = InstantMillisSerializer::class)
    val timestamp: Instant,
) : HeightSupport
