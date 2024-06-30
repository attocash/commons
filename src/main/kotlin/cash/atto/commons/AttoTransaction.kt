@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.io.Buffer
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class AttoTransaction(
    @ProtoNumber(0)
    val block: AttoBlock,
    @ProtoNumber(1)
    @Contextual
    val signature: AttoSignature,
    @ProtoNumber(2)
    @Contextual
    val work: AttoWork,
) : HeightSupport,
    AttoSerializable {
    @Transient
    val hash = block.hash

    @Transient
    override val height = block.height

    companion object {
        const val SIZE = 72

        fun fromBuffer(
            network: AttoNetwork,
            buffer: Buffer,
        ): AttoTransaction? {
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

            if (!transaction.isValid(network)) {
                return null
            }

            return transaction
        }
    }

    /**
     * Minimal block validation. This method doesn't check this transaction against the ledger so further validations are required.
     */
    fun isValid(network: AttoNetwork): Boolean {
        if (!block.isValid()) {
            return false
        }

        if (block is PreviousSupport && !work.isValid(network, block.timestamp, block.previous)) {
            return false
        }

        if (block is AttoOpenBlock && !work.isValid(network, block.timestamp, block.publicKey)) {
            return false
        }

        if (!signature.isValid(block.publicKey, block.hash)) {
            return false
        }

        return true
    }

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
