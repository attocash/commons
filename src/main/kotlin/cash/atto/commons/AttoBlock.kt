package cash.atto.commons

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

val maxVersion: UShort = 0U

enum class AttoBlockType(val code: UByte, val size: Int) {
    OPEN(0u, 115),
    RECEIVE(1u, 123),
    SEND(2u, 131),
    CHANGE(3u, 123),

    UNKNOWN(UByte.MAX_VALUE, 0);

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
        return version <= maxVersion && timestamp <= Clock.System.now()
    }
}

interface PreviousSupport {
    val height: ULong
    val previous: AttoHash
}

interface ReceiveSupport {
    val sendHash: AttoHash
}

interface RepresentativeSupport {
    val representative: AttoPublicKey
}

@Serializable
@SerialName("SEND")
data class AttoSendBlock(
    override val version: UShort,
    override val publicKey: AttoPublicKey,
    override val height: ULong,
    override val balance: AttoAmount,
    override val timestamp: Instant,
    override val previous: AttoHash,
    val receiverPublicKey: AttoPublicKey,
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
                publicKey = serializedBlock.getPublicKey(),
                height = serializedBlock.getULong(),
                balance = serializedBlock.getAmount(),
                timestamp = serializedBlock.getInstant(),
                previous = serializedBlock.getBlockHash(),
                receiverPublicKey = serializedBlock.getPublicKey(),
                amount = serializedBlock.getAmount(),
            )
        }
    }

    override fun toByteBuffer(): AttoByteBuffer {
        val byteBuffer = AttoByteBuffer(type.size)
        return byteBuffer
            .add(type)
            .add(version)
            .add(publicKey)
            .add(height)
            .add(balance)
            .add(timestamp)
            .add(previous)
            .add(receiverPublicKey)
            .add(amount)
    }

    override fun isValid(): Boolean {
        return super.isValid() && height > 1u && amount.raw > 0u && receiverPublicKey != publicKey
    }
}

@Serializable
@SerialName("RECEIVE")
data class AttoReceiveBlock(
    override val version: UShort,
    override val publicKey: AttoPublicKey,
    override val height: ULong,
    override val balance: AttoAmount,
    override val timestamp: Instant,
    override val previous: AttoHash,
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
                publicKey = serializedBlock.getPublicKey(),
                height = serializedBlock.getULong(),
                balance = serializedBlock.getAmount(),
                timestamp = serializedBlock.getInstant(),
                previous = serializedBlock.getBlockHash(),
                sendHash = serializedBlock.getBlockHash()
            )
        }
    }

    override fun toByteBuffer(): AttoByteBuffer {
        val byteBuffer = AttoByteBuffer(type.size)
        return byteBuffer
            .add(type)
            .add(version)
            .add(publicKey)
            .add(height)
            .add(balance)
            .add(timestamp)
            .add(previous)
            .add(sendHash)
    }

    override fun isValid(): Boolean {
        return super.isValid() && height > 1u && balance > AttoAmount.MIN
    }
}

@Serializable
@SerialName("OPEN")
data class AttoOpenBlock(
    override val version: UShort,
    override val publicKey: AttoPublicKey,
    override val balance: AttoAmount,
    override val timestamp: Instant,
    override val sendHash: AttoHash,
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
                publicKey = serializedBlock.getPublicKey(),
                balance = serializedBlock.getAmount(),
                timestamp = serializedBlock.getInstant(),
                sendHash = serializedBlock.getBlockHash(),
                representative = serializedBlock.getPublicKey(),
            )
        }
    }

    override fun toByteBuffer(): AttoByteBuffer {
        val byteBuffer = AttoByteBuffer(type.size)
        return byteBuffer
            .add(type)
            .add(version)
            .add(publicKey)
            .add(balance)
            .add(timestamp)
            .add(sendHash)
            .add(representative)
    }

}


@Serializable
@SerialName("CHANGE")
data class AttoChangeBlock(
    override val version: UShort,
    override val publicKey: AttoPublicKey,
    override val height: ULong,
    override val balance: AttoAmount,
    override val timestamp: Instant,
    override val previous: AttoHash,
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
                version = serializedBlock.getUShort(), // 2 2
                publicKey = serializedBlock.getPublicKey(), // 32 34
                height = serializedBlock.getULong(), // 8 42
                balance = serializedBlock.getAmount(), // 8 50
                timestamp = serializedBlock.getInstant(), // 8 58
                previous = serializedBlock.getBlockHash(), // 32 90
                representative = serializedBlock.getPublicKey(), // 32 122
            )
        }
    }

    override fun toByteBuffer(): AttoByteBuffer {
        val byteBuffer = AttoByteBuffer(type.size)
        return byteBuffer
            .add(type)
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