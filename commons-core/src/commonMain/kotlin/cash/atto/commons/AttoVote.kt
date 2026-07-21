package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

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

        internal fun readFromBuffer(buffer: Buffer): AttoVote =
            AttoVote(
                version = buffer.readAttoVersion(),
                algorithm = buffer.readAttoAlgorithm(),
                publicKey = buffer.readAttoPublicKey(),
                blockAlgorithm = buffer.readAttoAlgorithm(),
                blockHash = buffer.readAttoHash(),
                timestamp = buffer.readInstant(),
            )

        @JsExport.Ignore
        @JvmStatic
        fun fromBuffer(buffer: Buffer): AttoVote {
            val vote = readFromBuffer(buffer)
            buffer.requireNoRemainingBytes("AttoVote")
            return vote
        }
    }

    fun isFinal(): Boolean = timestamp == finalTimestamp

    fun isValid(): Boolean = version.value <= 0U

    @JsExport.Ignore
    override fun toBuffer(): Buffer =
        Buffer().apply {
            this.writeAttoVersion(version)
            this.writeAttoAlgorithm(algorithm)
            this.writeAttoPublicKey(publicKey)
            this.writeAttoAlgorithm(blockAlgorithm)
            this.writeAttoHash(blockHash)
            this.writeInstant(timestamp)
        }
}

data class AttoSignedVote(
    val vote: AttoVote,
    val signature: AttoSignature,
) : AttoHashable,
    AttoSerializable {
    override val hash by lazy { vote.hash }

    companion object {
        @JvmStatic
        fun fromBuffer(buffer: Buffer): AttoSignedVote {
            val vote = AttoVote.readFromBuffer(buffer)
            val signedVote =
                AttoSignedVote(
                    vote = vote,
                    signature = buffer.readAttoSignature(),
                )
            buffer.requireNoRemainingBytes("AttoSignedVote")
            return signedVote
        }
    }

    fun isFinal() = vote.isFinal()

    @JvmSynthetic
    suspend fun isValid(): Boolean = vote.isValid() && signature.isValid(vote.publicKey, hash)

    override fun toBuffer(): Buffer =
        Buffer().apply {
            val serializedVote = vote.toBuffer()
            this.write(serializedVote, serializedVote.size)
            this.writeAttoSignature(signature)
        }
}

@Deprecated(
    "Moved to AttoVote.fromBuffer(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoVote.fromBuffer(buffer)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoVote.Companion.fromBuffer(buffer: Buffer): AttoVote = AttoVote.fromBuffer(buffer)

@Deprecated(
    "Moved to AttoSignedVote.fromBuffer(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoSignedVote.fromBuffer(buffer)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoSignedVote.Companion.fromBuffer(buffer: Buffer): AttoSignedVote = AttoSignedVote.fromBuffer(buffer)

object AttoSignedVoteAsByteArraySerializer : KSerializer<AttoSignedVote> {
    override val descriptor = PrimitiveSerialDescriptor("AttoSignedVoteAsByteArray", PrimitiveKind.BYTE)

    override fun serialize(
        encoder: Encoder,
        value: AttoSignedVote,
    ) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.toBuffer().readByteArray())
    }

    override fun deserialize(decoder: Decoder): AttoSignedVote =
        AttoSignedVote.fromBuffer(decoder.decodeSerializableValue(ByteArraySerializer()).toBuffer())
}
