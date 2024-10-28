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
) : AttoHashable, AttoSerializable {
    override val hash by lazy { vote.hash }

    companion object {}

    fun isFinal() = vote.isFinal()

    fun isValid(): Boolean {
        return signature.isValid(publicKey, hash)
    }

    override fun toBuffer(): Buffer {
        return Buffer().apply {
            val serializedVote = vote.toBuffer()
            this.write(serializedVote, serializedVote.size)
            this.writeAttoAlgorithm(algorithm)
            this.writeAttoPublicKey(publicKey)
            this.writeAttoSignature(signature)
        }
    }
}

fun AttoVote.Companion.fromBuffer(buffer: Buffer): AttoVote {
    return AttoVote(
        buffer.readAttoAlgorithm(),
        buffer.readAttoHash(),
        buffer.readInstant()
    )
}

fun AttoSignedVote.Companion.fromBuffer(buffer: Buffer): AttoSignedVote {
    val vote = AttoVote.fromBuffer(buffer)
    return AttoSignedVote(
        vote,
        buffer.readAttoAlgorithm(),
        buffer.readAttoPublicKey(),
        buffer.readAttoSignature(),
    )
}
