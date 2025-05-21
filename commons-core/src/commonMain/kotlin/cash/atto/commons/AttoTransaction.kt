@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.io.Buffer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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

    /**
     * Minimal block validation. This method doesn't check this transaction against the ledger so further validations are required.
     */
    fun isValid(): Boolean {
        if (!block.isValid()) {
            return false
        }

        if (!work.isValid(block)) {
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
