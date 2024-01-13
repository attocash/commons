@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class AttoTransaction(
    @ProtoNumber(0)
    val block: AttoBlock,
    @ProtoNumber(1)
    val signature: AttoSignature,
    @ProtoNumber(2)
    val work: AttoWork
) : HeightSupport {
    @Transient
    val hash = block.hash

    @Transient
    override val height = block.height

    companion object {
        const val size = 72

        fun fromByteBuffer(network: AttoNetwork, byteBuffer: AttoByteBuffer): AttoTransaction? {
            if (size > byteBuffer.size) {
                return null
            }

            val block = AttoBlock.fromByteBuffer(byteBuffer) ?: return null

            val transaction = AttoTransaction(
                block = block,
                signature = byteBuffer.getSignature(),
                work = byteBuffer.getWork(),
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
    fun getTotalSize(): Int {
        return size + block.type.size
    }

    fun toByteBuffer(): AttoByteBuffer {
        return AttoByteBuffer(getTotalSize())
            .add(block.toByteBuffer())
            .add(signature)
            .add(work)
    }

    override fun toString(): String {
        return "AttoTransaction(hash=$hash, block=$block, signature=$signature, work=$work, hash=$hash)"
    }


}