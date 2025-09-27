package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.ExperimentalJsExport

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
@Serializable
data class AttoVote(
    val version: AttoVersion,
    val algorithm: AttoAlgorithm,
    val publicKey: AttoPublicKey,
    val blockAlgorithm: AttoAlgorithm,
    val blockHash: AttoHash,
    val timestamp: AttoInstant,
) : AttoHashable,
    AttoSerializable {
    override val hash by lazy { toBuffer().hash() }

    companion object {
        val finalTimestamp = AttoInstant.fromEpochMilliseconds(Long.MAX_VALUE)
    }

    fun isFinal(): Boolean {
        return timestamp == finalTimestamp
    }

    fun isValid(): Boolean {
        return version.value <= 0U
    }

    override fun toBuffer(): Buffer {
        return Buffer().apply {
            this.writeAttoVersion(version)
            this.writeAttoAlgorithm(algorithm)
            this.writeAttoPublicKey(publicKey)
            this.writeAttoAlgorithm(blockAlgorithm)
            this.writeAttoHash(blockHash)
            this.writeInstant(timestamp)
        }
    }
}

data class AttoSignedVote(
    val vote: AttoVote,
    val signature: AttoSignature,
) : AttoHashable,
    AttoSerializable {
    override val hash by lazy { vote.hash }

    companion object {}

    fun isFinal() = vote.isFinal()

    fun isValid(): Boolean {
        return vote.isValid() && signature.isValid(vote.publicKey, hash)
    }

    override fun toBuffer(): Buffer {
        return Buffer().apply {
            val serializedVote = vote.toBuffer()
            this.write(serializedVote, serializedVote.size)
            this.writeAttoSignature(signature)
        }
    }
}

fun AttoVote.Companion.fromBuffer(buffer: Buffer): AttoVote {
    return AttoVote(
        version = buffer.readAttoVersion(),
        algorithm = buffer.readAttoAlgorithm(),
        publicKey = buffer.readAttoPublicKey(),
        blockAlgorithm = buffer.readAttoAlgorithm(),
        blockHash = buffer.readAttoHash(),
        timestamp = buffer.readInstant(),
    )
}

fun AttoSignedVote.Companion.fromBuffer(buffer: Buffer): AttoSignedVote {
    val vote = AttoVote.fromBuffer(buffer)
    return AttoSignedVote(
        vote = vote,
        signature = buffer.readAttoSignature(),
    )
}

object AttoSignedVoteAsByteArraySerializer : KSerializer<AttoSignedVote> {
    override val descriptor: SerialDescriptor = ByteArraySerializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoSignedVote,
    ) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.toBuffer().readByteArray())
    }

    override fun deserialize(decoder: Decoder): AttoSignedVote =
        AttoSignedVote.fromBuffer(decoder.decodeSerializableValue(ByteArraySerializer()).toBuffer())
}
