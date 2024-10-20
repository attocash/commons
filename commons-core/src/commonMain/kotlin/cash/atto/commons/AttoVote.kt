package cash.atto.commons

import kotlinx.datetime.Instant
import kotlinx.io.Buffer
import kotlinx.serialization.Serializable

@Serializable
data class AttoVote(
    val blockAlgorithm: AttoAlgorithm,
    val blockHash: AttoHash,
    val timestamp: Instant,
) : AttoHashable, AttoSerializable {
    override val hash by lazy { toBuffer().hash() }

    companion object {
        val finalTimestamp = Instant.fromEpochMilliseconds(Long.MAX_VALUE)
    }

    fun isFinal(): Boolean {
        return timestamp == finalTimestamp
    }

    override fun toBuffer(): Buffer {
        return Buffer().apply {
            this.writeAttoAlgorithm(blockAlgorithm)
            this.writeAttoHash(blockHash)
            this.writeInstant(timestamp)
        }
    }
}


data class AttoSignedVote(
    val vote: AttoVote,
    val algorithm: AttoAlgorithm,
    val publicKey: AttoPublicKey,
    val signature: AttoSignature
) : AttoHashable {
    override val hash by lazy { vote.hash }

    companion object {}

    fun isValid(): Boolean {
        return signature.isValid(publicKey, hash)
    }
}
