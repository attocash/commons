@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoNumber

val maxVersion: UShort = 0U

enum class AttoBlockType(val code: UByte, val size: Int) {
    @ProtoNumber(255)
    UNKNOWN(UByte.MAX_VALUE, 0),

    @ProtoNumber(0)
    OPEN(0u, 117),

    @ProtoNumber(1)
    RECEIVE(1u, 125),

    @ProtoNumber(2)
    SEND(2u, 133),

    @ProtoNumber(3)
    CHANGE(3u, 124);

    companion object {
        private val map = entries.associateBy(AttoBlockType::code)
        fun from(code: UByte): AttoBlockType {
            return map.getOrDefault(code, UNKNOWN)
        }
    }
}

interface HeightSupport {
    val height: ULong
}

@Serializable
sealed interface AttoBlock : HeightSupport {
    val type: AttoBlockType
    val algorithm: AttoAlgorithm

    val hash: AttoHash

    val version: UShort
    val publicKey: AttoPublicKey
    override val height: ULong
    val balance: AttoAmount
    val timestamp: Instant

    fun toByteBuffer(): AttoByteBuffer

    companion object {
        fun fromByteBuffer(serializedBlock: AttoByteBuffer): AttoBlock? {
            val type = serializedBlock.getBlockType()
            return when (type) {
                AttoBlockType.SEND -> {
                    AttoSendBlock.fromByteBuffer(serializedBlock)
                }

                AttoBlockType.RECEIVE -> {
                    AttoReceiveBlock.fromByteBuffer(serializedBlock)
                }

                AttoBlockType.OPEN -> {
                    AttoOpenBlock.fromByteBuffer(serializedBlock)
                }

                AttoBlockType.CHANGE -> {
                    AttoChangeBlock.fromByteBuffer(serializedBlock)
                }

                AttoBlockType.UNKNOWN -> {
                    return null
                }
            }
        }
    }

    fun isValid(): Boolean {
        return version <= maxVersion &&
                timestamp <= Clock.System.now() &&
                algorithm != AttoAlgorithm.UNKNOWN
    }
}

interface PreviousSupport {
    val height: ULong
    val previous: AttoHash
}

interface ReceiveSupport {
    val sendHashAlgorithm: AttoAlgorithm
    val sendHash: AttoHash
}

interface RepresentativeSupport {
    val representative: AttoPublicKey
}

@Serializable
@SerialName("SEND")
data class AttoSendBlock(
    @ProtoNumber(0)
    override val version: UShort,
    @ProtoNumber(1)
    override val algorithm: AttoAlgorithm,
    @Contextual
    @ProtoNumber(2)
    override val publicKey: AttoPublicKey,
    @ProtoNumber(3)
    override val height: ULong,
    @ProtoNumber(4)
    override val balance: AttoAmount,
    @ProtoNumber(5)
    override val timestamp: Instant,
    @Contextual
    @ProtoNumber(6)
    override val previous: AttoHash,
    @ProtoNumber(7)
    val receiverPublicKeyAlgorithm: AttoAlgorithm,
    @Contextual
    @ProtoNumber(8)
    val receiverPublicKey: AttoPublicKey,
    @ProtoNumber(9)
    val amount: AttoAmount,
) : AttoBlock, PreviousSupport {
    @Transient
    override val type = AttoBlockType.SEND

    @Transient
    override val hash = toByteBuffer().toHash()

    companion object {
        internal fun fromByteBuffer(serializedBlock: AttoByteBuffer): AttoSendBlock? {
            if (AttoBlockType.SEND.size > serializedBlock.size) {
                return null
            }

            val blockType = serializedBlock.getBlockType(0)
            if (blockType != AttoBlockType.SEND) {
                throw IllegalArgumentException("Invalid block type: $blockType")
            }

            return AttoSendBlock(
                version = serializedBlock.getUShort(),
                algorithm = serializedBlock.getAlgorithm(),
                publicKey = serializedBlock.getPublicKey(),
                height = serializedBlock.getULong(),
                balance = serializedBlock.getAmount(),
                timestamp = serializedBlock.getInstant(),
                previous = serializedBlock.getBlockHash(),
                receiverPublicKeyAlgorithm = serializedBlock.getAlgorithm(),
                receiverPublicKey = serializedBlock.getPublicKey(),
                amount = serializedBlock.getAmount(),
            )
        }
    }

    override fun toByteBuffer(): AttoByteBuffer {
        val byteBuffer = AttoByteBuffer(type.size)
        return byteBuffer
            .add(type)
            .add(algorithm)
            .add(version)
            .add(publicKey)
            .add(height)
            .add(balance)
            .add(timestamp)
            .add(previous)
            .add(receiverPublicKeyAlgorithm)
            .add(receiverPublicKey)
            .add(amount)
    }

    override fun isValid(): Boolean {
        return super.isValid() &&
                height > 1u &&
                amount.raw > 0u &&
                receiverPublicKey != publicKey &&
                receiverPublicKeyAlgorithm != AttoAlgorithm.UNKNOWN &&
                receiverPublicKeyAlgorithm.publicKeySize == receiverPublicKey.value.size
    }
}

@Serializable
@SerialName("RECEIVE")
data class AttoReceiveBlock(
    @ProtoNumber(0)
    override val version: UShort,
    @ProtoNumber(1)
    override val algorithm: AttoAlgorithm,
    @Contextual
    @ProtoNumber(2)
    override val publicKey: AttoPublicKey,
    @ProtoNumber(3)
    override val height: ULong,
    @ProtoNumber(4)
    override val balance: AttoAmount,
    @ProtoNumber(5)
    override val timestamp: Instant,
    @Contextual
    @ProtoNumber(6)
    override val previous: AttoHash,
    @ProtoNumber(7)
    override val sendHashAlgorithm: AttoAlgorithm,
    @Contextual
    @ProtoNumber(8)
    override val sendHash: AttoHash,
) : AttoBlock, PreviousSupport, ReceiveSupport {
    @Transient
    override val type = AttoBlockType.RECEIVE

    @Transient
    override val hash = toByteBuffer().toHash()

    companion object {

        internal fun fromByteBuffer(serializedBlock: AttoByteBuffer): AttoReceiveBlock? {
            if (AttoBlockType.RECEIVE.size > serializedBlock.size) {
                return null
            }

            val blockType = serializedBlock.getBlockType(0)
            if (blockType != AttoBlockType.RECEIVE) {
                throw IllegalArgumentException("Invalid block type: $blockType")
            }

            return AttoReceiveBlock(
                version = serializedBlock.getUShort(),
                algorithm = serializedBlock.getAlgorithm(),
                publicKey = serializedBlock.getPublicKey(),
                height = serializedBlock.getULong(),
                balance = serializedBlock.getAmount(),
                timestamp = serializedBlock.getInstant(),
                previous = serializedBlock.getBlockHash(),
                sendHashAlgorithm = serializedBlock.getAlgorithm(),
                sendHash = serializedBlock.getBlockHash()
            )
        }
    }

    override fun toByteBuffer(): AttoByteBuffer {
        val byteBuffer = AttoByteBuffer(type.size)
        return byteBuffer
            .add(type)
            .add(algorithm)
            .add(version)
            .add(publicKey)
            .add(height)
            .add(balance)
            .add(timestamp)
            .add(previous)
            .add(sendHashAlgorithm)
            .add(sendHash)
    }

    override fun isValid(): Boolean {
        return super.isValid() &&
                height > 1u &&
                balance > AttoAmount.MIN &&
                sendHashAlgorithm != AttoAlgorithm.UNKNOWN &&
                sendHash.value.size == sendHashAlgorithm.hashSize
    }
}

@Serializable
@SerialName("OPEN")
data class AttoOpenBlock(
    @ProtoNumber(0)
    override val version: UShort,
    @ProtoNumber(1)
    override val algorithm: AttoAlgorithm,
    @Contextual
    @ProtoNumber(2)
    override val publicKey: AttoPublicKey,
    @ProtoNumber(3)
    override val balance: AttoAmount,
    @ProtoNumber(4)
    override val timestamp: Instant,
    @ProtoNumber(5)
    override val sendHashAlgorithm: AttoAlgorithm,
    @Contextual
    @ProtoNumber(6)
    override val sendHash: AttoHash,
    @Contextual
    @ProtoNumber(7)
    override val representative: AttoPublicKey,
) : AttoBlock, ReceiveSupport, RepresentativeSupport {
    @Transient
    override val type = AttoBlockType.OPEN

    @Transient
    override val hash = toByteBuffer().toHash()

    @Transient
    override val height = 1UL

    companion object {
        internal fun fromByteBuffer(serializedBlock: AttoByteBuffer): AttoOpenBlock? {
            if (AttoBlockType.OPEN.size > serializedBlock.size) {
                return null
            }

            val blockType = serializedBlock.getBlockType(0)
            if (blockType != AttoBlockType.OPEN) {
                throw IllegalArgumentException("Invalid block type: $blockType")
            }

            return AttoOpenBlock(
                version = serializedBlock.getUShort(),
                algorithm = serializedBlock.getAlgorithm(),
                publicKey = serializedBlock.getPublicKey(),
                balance = serializedBlock.getAmount(),
                timestamp = serializedBlock.getInstant(),
                sendHashAlgorithm = serializedBlock.getAlgorithm(),
                sendHash = serializedBlock.getBlockHash(),
                representative = serializedBlock.getPublicKey(),
            )
        }
    }

    override fun toByteBuffer(): AttoByteBuffer {
        val byteBuffer = AttoByteBuffer(type.size)
        return byteBuffer
            .add(type)
            .add(algorithm)
            .add(version)
            .add(publicKey)
            .add(balance)
            .add(timestamp)
            .add(sendHashAlgorithm)
            .add(sendHash)
            .add(representative)
    }

    override fun isValid(): Boolean {
        return super.isValid() &&
                balance > AttoAmount.MIN &&
                sendHashAlgorithm != AttoAlgorithm.UNKNOWN &&
                sendHash.value.size == sendHashAlgorithm.hashSize
    }

}


@Serializable
@SerialName("CHANGE")
data class AttoChangeBlock(
    @ProtoNumber(0)
    override val version: UShort,
    @ProtoNumber(1)
    override val algorithm: AttoAlgorithm,
    @Contextual
    @ProtoNumber(2)
    override val publicKey: AttoPublicKey,
    @ProtoNumber(3)
    override val height: ULong,
    @ProtoNumber(4)
    override val balance: AttoAmount,
    @ProtoNumber(5)
    override val timestamp: Instant,
    @Contextual
    @ProtoNumber(6)
    override val previous: AttoHash,
    @Contextual
    @ProtoNumber(7)
    override val representative: AttoPublicKey,
) : AttoBlock, PreviousSupport, RepresentativeSupport {
    @Transient
    override val type = AttoBlockType.CHANGE

    @Transient
    override val hash = toByteBuffer().toHash()

    companion object {
        internal fun fromByteBuffer(serializedBlock: AttoByteBuffer): AttoChangeBlock? {
            if (AttoBlockType.CHANGE.size > serializedBlock.size) {
                return null
            }

            val blockType = serializedBlock.getBlockType(0)
            if (blockType != AttoBlockType.CHANGE) {
                throw IllegalArgumentException("Invalid block type: $blockType")
            }

            return AttoChangeBlock(
                version = serializedBlock.getUShort(),
                algorithm = serializedBlock.getAlgorithm(),
                publicKey = serializedBlock.getPublicKey(),
                height = serializedBlock.getULong(),
                balance = serializedBlock.getAmount(),
                timestamp = serializedBlock.getInstant(),
                previous = serializedBlock.getBlockHash(),
                representative = serializedBlock.getPublicKey(),
            )
        }
    }

    override fun toByteBuffer(): AttoByteBuffer {
        val byteBuffer = AttoByteBuffer(type.size)
        return byteBuffer
            .add(type)
            .add(algorithm)
            .add(version)
            .add(publicKey)
            .add(height)
            .add(balance)
            .add(timestamp)
            .add(previous)
            .add(representative)
    }

    override fun isValid(): Boolean {
        return super.isValid() && height > 1u
    }
}