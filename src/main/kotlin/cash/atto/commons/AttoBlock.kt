@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import cash.atto.commons.serialiazers.InstantMillisSerializer
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.Buffer
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoNumber

val maxVersion = AttoVersion(0U)

enum class AttoBlockType(
    val code: UByte,
    val size: Int,
) {
    @ProtoNumber(255)
    UNKNOWN(UByte.MAX_VALUE, 0),

    @ProtoNumber(0)
    OPEN(0u, 117),

    @ProtoNumber(1)
    RECEIVE(1u, 125),

    @ProtoNumber(2)
    SEND(2u, 133),

    @ProtoNumber(3)
    CHANGE(3u, 124),
    ;

    companion object {
        private val map = entries.associateBy(AttoBlockType::code)

        fun from(code: UByte): AttoBlockType {
            return map.getOrDefault(code, UNKNOWN)
        }
    }
}

interface HeightSupport {
    val height: AttoHeight
}

@Serializable
sealed interface AttoBlock : HeightSupport {
    val type: AttoBlockType
    val algorithm: AttoAlgorithm

    val hash: AttoHash

    val version: AttoVersion
    val publicKey: AttoPublicKey
    override val height: AttoHeight
    val balance: AttoAmount
    val timestamp: Instant

    fun toBuffer(): Buffer

    companion object {
        fun fromBuffer(serializedBlock: Buffer): AttoBlock? {
            val type =
                Buffer().let {
                    serializedBlock.copyTo(it, 0, 1)
                    it.readAttoBlockType()
                }
            return when (type) {
                AttoBlockType.SEND -> {
                    AttoSendBlock.fromBuffer(serializedBlock)
                }

                AttoBlockType.RECEIVE -> {
                    AttoReceiveBlock.fromBuffer(serializedBlock)
                }

                AttoBlockType.OPEN -> {
                    AttoOpenBlock.fromBuffer(serializedBlock)
                }

                AttoBlockType.CHANGE -> {
                    AttoChangeBlock.fromBuffer(serializedBlock)
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
    val height: AttoHeight
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
@SerialName("AttoSendBlock")
data class AttoSendBlock(
    @ProtoNumber(0)
    override val version: AttoVersion,
    @ProtoNumber(1)
    override val algorithm: AttoAlgorithm,
    @Contextual
    @ProtoNumber(2)
    override val publicKey: AttoPublicKey,
    @ProtoNumber(3)
    override val height: AttoHeight,
    @ProtoNumber(4)
    override val balance: AttoAmount,
    @ProtoNumber(5)
    @Serializable(with = InstantMillisSerializer::class)
    override val timestamp: Instant,
    @Contextual
    @ProtoNumber(6)
    override val previous: AttoHash,
    @ProtoNumber(7)
    val receiverAlgorithm: AttoAlgorithm,
    @Contextual
    @ProtoNumber(8)
    val receiverPublicKey: AttoPublicKey,
    @ProtoNumber(9)
    val amount: AttoAmount,
) : AttoBlock,
    PreviousSupport {
    @Transient
    override val type = AttoBlockType.SEND

    @Transient
    override val hash = toBuffer().hash()

    companion object {
        internal fun fromBuffer(serializedBlock: Buffer): AttoSendBlock? {
            if (AttoBlockType.SEND.size > serializedBlock.size) {
                return null
            }

            val blockType = serializedBlock.readAttoBlockType()
            if (blockType != AttoBlockType.SEND) {
                throw IllegalArgumentException("Invalid block type: $blockType")
            }

            return AttoSendBlock(
                version = serializedBlock.readAttoVersion(),
                algorithm = serializedBlock.readAttoAlgorithm(),
                publicKey = serializedBlock.readAttoPublicKey(),
                height = serializedBlock.readAttoHeight(),
                balance = serializedBlock.readAttoAmount(),
                timestamp = serializedBlock.readInstant(),
                previous = serializedBlock.readAttoHash(),
                receiverAlgorithm = serializedBlock.readAttoAlgorithm(),
                receiverPublicKey = serializedBlock.readAttoPublicKey(),
                amount = serializedBlock.readAttoAmount(),
            )
        }
    }

    override fun toBuffer(): Buffer {
        return Buffer().apply {
            this.writeAttoBlockType(type)
            this.writeAttoAlgorithm(algorithm)
            this.writeAttoVersion(version)
            this.writeAttoPublicKey(publicKey)
            this.writeAttoHeight(height)
            this.writeAttoAmount(balance)
            this.writeInstant(timestamp)
            this.writeAttoHash(previous)
            this.writeAttoAlgorithm(receiverAlgorithm)
            this.writeAttoPublicKey(receiverPublicKey)
            this.writeAttoAmount(amount)
        }
    }

    override fun isValid(): Boolean {
        return super.isValid() &&
            height > AttoHeight(1u) &&
            amount.raw > 0u &&
            receiverPublicKey != publicKey &&
            receiverAlgorithm != AttoAlgorithm.UNKNOWN &&
            receiverAlgorithm.publicKeySize == receiverPublicKey.value.size
    }
}

@Serializable
@SerialName("AttoReceiveBlock")
data class AttoReceiveBlock(
    @ProtoNumber(0)
    override val version: AttoVersion,
    @ProtoNumber(1)
    override val algorithm: AttoAlgorithm,
    @Contextual
    @ProtoNumber(2)
    override val publicKey: AttoPublicKey,
    @ProtoNumber(3)
    override val height: AttoHeight,
    @ProtoNumber(4)
    override val balance: AttoAmount,
    @ProtoNumber(5)
    @Serializable(with = InstantMillisSerializer::class)
    override val timestamp: Instant,
    @Contextual
    @ProtoNumber(6)
    override val previous: AttoHash,
    @ProtoNumber(7)
    override val sendHashAlgorithm: AttoAlgorithm,
    @Contextual
    @ProtoNumber(8)
    override val sendHash: AttoHash,
) : AttoBlock,
    PreviousSupport,
    ReceiveSupport {
    @Transient
    override val type = AttoBlockType.RECEIVE

    @Transient
    override val hash = toBuffer().hash()

    companion object {
        internal fun fromBuffer(serializedBlock: Buffer): AttoReceiveBlock? {
            if (AttoBlockType.RECEIVE.size > serializedBlock.size) {
                return null
            }

            val blockType = serializedBlock.readAttoBlockType()
            if (blockType != AttoBlockType.RECEIVE) {
                throw IllegalArgumentException("Invalid block type: $blockType")
            }

            return AttoReceiveBlock(
                version = serializedBlock.readAttoVersion(),
                algorithm = serializedBlock.readAttoAlgorithm(),
                publicKey = serializedBlock.readAttoPublicKey(),
                height = serializedBlock.readAttoHeight(),
                balance = serializedBlock.readAttoAmount(),
                timestamp = serializedBlock.readInstant(),
                previous = serializedBlock.readAttoHash(),
                sendHashAlgorithm = serializedBlock.readAttoAlgorithm(),
                sendHash = serializedBlock.readAttoHash(),
            )
        }
    }

    override fun toBuffer(): Buffer {
        return Buffer().apply {
            this.writeAttoBlockType(type)
            this.writeAttoAlgorithm(algorithm)
            this.writeAttoVersion(version)
            this.writeAttoPublicKey(publicKey)
            this.writeAttoHeight(height)
            this.writeAttoAmount(balance)
            this.writeInstant(timestamp)
            this.writeAttoHash(previous)
            this.writeAttoAlgorithm(sendHashAlgorithm)
            this.writeAttoHash(sendHash)
        }
    }

    override fun isValid(): Boolean {
        return super.isValid() &&
            height > AttoHeight(1u) &&
            balance > AttoAmount.MIN &&
            sendHashAlgorithm != AttoAlgorithm.UNKNOWN &&
            sendHash.value.size == sendHashAlgorithm.hashSize
    }
}

@Serializable
@SerialName("AttoOpenBlock")
data class AttoOpenBlock(
    @ProtoNumber(0)
    override val version: AttoVersion,
    @ProtoNumber(1)
    override val algorithm: AttoAlgorithm,
    @Contextual
    @ProtoNumber(2)
    override val publicKey: AttoPublicKey,
    @ProtoNumber(3)
    override val balance: AttoAmount,
    @ProtoNumber(4)
    @Serializable(with = InstantMillisSerializer::class)
    override val timestamp: Instant,
    @ProtoNumber(5)
    override val sendHashAlgorithm: AttoAlgorithm,
    @Contextual
    @ProtoNumber(6)
    override val sendHash: AttoHash,
    @Contextual
    @ProtoNumber(7)
    override val representative: AttoPublicKey,
) : AttoBlock,
    ReceiveSupport,
    RepresentativeSupport {
    @Transient
    override val type = AttoBlockType.OPEN

    @Transient
    override val hash = toBuffer().hash()

    @Transient
    override val height = AttoHeight(1UL)

    companion object {
        internal fun fromBuffer(serializedBlock: Buffer): AttoOpenBlock? {
            if (AttoBlockType.OPEN.size > serializedBlock.size) {
                return null
            }

            val blockType = serializedBlock.readAttoBlockType()
            if (blockType != AttoBlockType.OPEN) {
                throw IllegalArgumentException("Invalid block type: $blockType")
            }

            return AttoOpenBlock(
                version = serializedBlock.readAttoVersion(),
                algorithm = serializedBlock.readAttoAlgorithm(),
                publicKey = serializedBlock.readAttoPublicKey(),
                balance = serializedBlock.readAttoAmount(),
                timestamp = serializedBlock.readInstant(),
                sendHashAlgorithm = serializedBlock.readAttoAlgorithm(),
                sendHash = serializedBlock.readAttoHash(),
                representative = serializedBlock.readAttoPublicKey(),
            )
        }
    }

    override fun toBuffer(): Buffer {
        return Buffer().apply {
            this.writeAttoBlockType(type)
            this.writeAttoAlgorithm(algorithm)
            this.writeAttoVersion(version)
            this.writeAttoPublicKey(publicKey)
            this.writeAttoAmount(balance)
            this.writeInstant(timestamp)
            this.writeAttoAlgorithm(sendHashAlgorithm)
            this.writeAttoHash(sendHash)
            this.writeAttoPublicKey(representative)
        }
    }

    override fun isValid(): Boolean {
        return super.isValid() &&
            balance > AttoAmount.MIN &&
            sendHashAlgorithm != AttoAlgorithm.UNKNOWN &&
            sendHash.value.size == sendHashAlgorithm.hashSize
    }
}

@Serializable
@SerialName("AttoChangeBlock")
data class AttoChangeBlock(
    @ProtoNumber(0)
    override val version: AttoVersion,
    @ProtoNumber(1)
    override val algorithm: AttoAlgorithm,
    @Contextual
    @ProtoNumber(2)
    override val publicKey: AttoPublicKey,
    @ProtoNumber(3)
    override val height: AttoHeight,
    @ProtoNumber(4)
    override val balance: AttoAmount,
    @ProtoNumber(5)
    @Serializable(with = InstantMillisSerializer::class)
    override val timestamp: Instant,
    @Contextual
    @ProtoNumber(6)
    override val previous: AttoHash,
    @Contextual
    @ProtoNumber(7)
    override val representative: AttoPublicKey,
) : AttoBlock,
    PreviousSupport,
    RepresentativeSupport {
    @Transient
    override val type = AttoBlockType.CHANGE

    @Transient
    override val hash = toBuffer().hash()

    companion object {
        internal fun fromBuffer(serializedBlock: Buffer): AttoChangeBlock? {
            if (AttoBlockType.CHANGE.size > serializedBlock.size) {
                return null
            }

            val blockType = serializedBlock.readAttoBlockType()
            if (blockType != AttoBlockType.CHANGE) {
                throw IllegalArgumentException("Invalid block type: $blockType")
            }

            return AttoChangeBlock(
                version = serializedBlock.readAttoVersion(),
                algorithm = serializedBlock.readAttoAlgorithm(),
                publicKey = serializedBlock.readAttoPublicKey(),
                height = serializedBlock.readAttoHeight(),
                balance = serializedBlock.readAttoAmount(),
                timestamp = serializedBlock.readInstant(),
                previous = serializedBlock.readAttoHash(),
                representative = serializedBlock.readAttoPublicKey(),
            )
        }
    }

    override fun toBuffer(): Buffer =
        Buffer().apply {
            this.writeAttoBlockType(type)
            this.writeAttoAlgorithm(algorithm)
            this.writeAttoVersion(version)
            this.writeAttoPublicKey(publicKey)
            this.writeAttoHeight(height)
            this.writeAttoAmount(balance)
            this.writeInstant(timestamp)
            this.writeAttoHash(previous)
            this.writeAttoPublicKey(representative)
        }

    override fun isValid(): Boolean = super.isValid() && height > AttoHeight(1u)
}
