package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JsExportForJs
@Serializable
data class AttoTransaction(
    val block: AttoBlock,
    val signature: AttoSignature,
    val work: AttoWork,
) : HeightSupport,
    AttoSerializable {
    val hash by lazy { block.hash }

    @Transient
    override val height = block.height

    companion object {
        const val SIZE = 72

        fun fromBuffer(buffer: Buffer): AttoTransaction? {
            if (SIZE > buffer.size) {
                return null
            }

            val block = AttoBlock.fromBuffer(buffer) ?: return null

            val transaction =
                AttoTransaction(
                    block = block,
                    signature = buffer.readAttoSignature(),
                    work = buffer.readAttoWork(),
                )

            if (!transaction.isValid()) {
                return null
            }

            return transaction
        }
    }

    fun validate(): AttoValidation {
        when (val blockValidation = block.validate()) {
            is AttoValidation.Ok -> Unit
            is AttoValidation.Error -> return blockValidation
        }


        if (!work.isValid(block)) {
            return AttoValidation.Error(
                "Work is invalid"
            )
        }

        if (!signature.isValid(block.publicKey, block.hash)) {
            return AttoValidation.Error(
                "Signature is invalid: publicKey=${block.publicKey}, hash=${block.hash}"
            )
        }

        return AttoValidation.Ok
    }


    /**
     * Minimal block validation. This method doesn't check this transaction against the ledger so further validations are required.
     */
    fun isValid(): Boolean = validate().isValid


    /**
     * Return the transaction and block size
     */
    fun getTotalSize(): Int = SIZE + block.type.size

    override fun toBuffer(): Buffer {
        val serializedBlock = block.toBuffer()
        return Buffer().apply {
            this.write(serializedBlock, serializedBlock.size)
            this.writeAttoSignature(signature)
            this.writeAttoWork(work)
        }
    }

    override fun toString(): String = "AttoTransaction(hash=$hash, block=$block, signature=$signature, work=$work, hash=$hash)"
}

object AttoTransactionAsByteArraySerializer : KSerializer<AttoTransaction> {
    override val descriptor = PrimitiveSerialDescriptor("AttoTransactionAsByteArray", PrimitiveKind.BYTE)

    override fun serialize(
        encoder: Encoder,
        value: AttoTransaction,
    ) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.toBuffer().readByteArray())
    }

    override fun deserialize(decoder: Decoder): AttoTransaction =
        AttoTransaction.fromBuffer(decoder.decodeSerializableValue(ByteArraySerializer()).toBuffer())!!
}
