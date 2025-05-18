@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import cash.atto.commons.serialiazer.InstantMillisSerializer
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.Buffer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Duration.Companion.minutes

val maxVersion = AttoVersion(0U)

enum class AttoBlockType(
    val code: UByte,
    val size: Int,
) {
    UNKNOWN(UByte.MAX_VALUE, 0),

    OPEN(0u, 117),

    RECEIVE(1u, 125),

    SEND(2u, 133),

    CHANGE(3u, 124),
    ;

    companion object {
        private val map = entries.associateBy(AttoBlockType::code)

        fun from(code: UByte): AttoBlockType {
            return map[code] ?: UNKNOWN
        }
    }
}

interface HeightSupport {
    val height: AttoHeight
}

@Serializable
sealed interface AttoBlock :
    HeightSupport,
    AttoHashable,
    AttoSerializable {
    override val hash: AttoHash

    val type: AttoBlockType
    val network: AttoNetwork
    val version: AttoVersion
    val algorithm: AttoAlgorithm
    val publicKey: AttoPublicKey
    override val height: AttoHeight
    val balance: AttoAmount
    val timestamp: Instant

    override fun toBuffer(): Buffer

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
            timestamp <= Clock.System.now() + 1.minutes
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
    val representativeAlgorithm: AttoAlgorithm
    val representativePublicKey: AttoPublicKey
}

@Serializable
@SerialName("SEND")
data class AttoSendBlock(
    override val network: AttoNetwork,
    override val version: AttoVersion,
    override val algorithm: AttoAlgorithm,
    override val publicKey: AttoPublicKey,
    override val height: AttoHeight,
    override val balance: AttoAmount,
    @Serializable(with = InstantMillisSerializer::class)
    override val timestamp: Instant,
    override val previous: AttoHash,
    val receiverAlgorithm: AttoAlgorithm,
    val receiverPublicKey: AttoPublicKey,
    val amount: AttoAmount,
) : AttoBlock,
    PreviousSupport {
    @Transient
    override val type = AttoBlockType.SEND

    override val hash by lazy { toBuffer().hash() }

    val address by lazy { AttoAddress(algorithm, publicKey) }
    val receiverAddress by lazy { AttoAddress(receiverAlgorithm, receiverPublicKey) }

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
                network = serializedBlock.readAttoNetwork(),
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
            this.writeAttoNetwork(network)
            this.writeAttoVersion(version)
            this.writeAttoAlgorithm(algorithm)
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
            receiverAlgorithm.publicKeySize == receiverPublicKey.value.size
    }
}

@Serializable
@SerialName("RECEIVE")
data class AttoReceiveBlock(
    override val network: AttoNetwork,
    override val version: AttoVersion,
    override val algorithm: AttoAlgorithm,
    override val publicKey: AttoPublicKey,
    override val height: AttoHeight,
    override val balance: AttoAmount,
    @Serializable(with = InstantMillisSerializer::class)
    override val timestamp: Instant,
    override val previous: AttoHash,
    override val sendHashAlgorithm: AttoAlgorithm,
    override val sendHash: AttoHash,
) : AttoBlock,
    PreviousSupport,
    ReceiveSupport {
    @Transient
    override val type = AttoBlockType.RECEIVE

    override val hash by lazy { toBuffer().hash() }

    val address by lazy { AttoAddress(algorithm, publicKey) }

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
                network = serializedBlock.readAttoNetwork(),
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
            this.writeAttoNetwork(network)
            this.writeAttoVersion(version)
            this.writeAttoAlgorithm(algorithm)
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
            sendHash.value.size == sendHashAlgorithm.hashSize
    }
}

@Serializable
@SerialName("OPEN")
data class AttoOpenBlock(
    override val network: AttoNetwork,
    override val version: AttoVersion,
    override val algorithm: AttoAlgorithm,
    override val publicKey: AttoPublicKey,
    override val balance: AttoAmount,
    @Serializable(with = InstantMillisSerializer::class)
    override val timestamp: Instant,
    override val sendHashAlgorithm: AttoAlgorithm,
    override val sendHash: AttoHash,
    override val representativeAlgorithm: AttoAlgorithm,
    override val representativePublicKey: AttoPublicKey,
) : AttoBlock,
    ReceiveSupport,
    RepresentativeSupport {
    @Transient
    override val type = AttoBlockType.OPEN

    override val hash by lazy { toBuffer().hash() }

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val height = AttoHeight(1UL)

    val address by lazy { AttoAddress(algorithm, publicKey) }
    val representativeAddress by lazy { AttoAddress(representativeAlgorithm, representativePublicKey) }

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
                network = serializedBlock.readAttoNetwork(),
                version = serializedBlock.readAttoVersion(),
                algorithm = serializedBlock.readAttoAlgorithm(),
                publicKey = serializedBlock.readAttoPublicKey(),
                balance = serializedBlock.readAttoAmount(),
                timestamp = serializedBlock.readInstant(),
                sendHashAlgorithm = serializedBlock.readAttoAlgorithm(),
                sendHash = serializedBlock.readAttoHash(),
                representativeAlgorithm = serializedBlock.readAttoAlgorithm(),
                representativePublicKey = serializedBlock.readAttoPublicKey(),
            )
        }
    }

    override fun toBuffer(): Buffer {
        return Buffer().apply {
            this.writeAttoBlockType(type)
            this.writeAttoNetwork(network)
            this.writeAttoVersion(version)
            this.writeAttoAlgorithm(algorithm)
            this.writeAttoPublicKey(publicKey)
            this.writeAttoAmount(balance)
            this.writeInstant(timestamp)
            this.writeAttoAlgorithm(sendHashAlgorithm)
            this.writeAttoHash(sendHash)
            this.writeAttoAlgorithm(representativeAlgorithm)
            this.writeAttoPublicKey(representativePublicKey)
        }
    }

    override fun isValid(): Boolean {
        return super.isValid() &&
            balance > AttoAmount.MIN &&
            sendHash.value.size == sendHashAlgorithm.hashSize
    }
}

@Serializable
@SerialName("CHANGE")
data class AttoChangeBlock(
    override val network: AttoNetwork,
    override val version: AttoVersion,
    override val algorithm: AttoAlgorithm,
    override val publicKey: AttoPublicKey,
    override val height: AttoHeight,
    override val balance: AttoAmount,
    @Serializable(with = InstantMillisSerializer::class)
    override val timestamp: Instant,
    override val previous: AttoHash,
    override val representativeAlgorithm: AttoAlgorithm,
    override val representativePublicKey: AttoPublicKey,
) : AttoBlock,
    PreviousSupport,
    RepresentativeSupport {
    @Transient
    override val type = AttoBlockType.CHANGE

    override val hash by lazy { toBuffer().hash() }

    val address by lazy { AttoAddress(algorithm, publicKey) }
    val representativeAddress by lazy { AttoAddress(representativeAlgorithm, representativePublicKey) }

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
                network = serializedBlock.readAttoNetwork(),
                version = serializedBlock.readAttoVersion(),
                algorithm = serializedBlock.readAttoAlgorithm(),
                publicKey = serializedBlock.readAttoPublicKey(),
                height = serializedBlock.readAttoHeight(),
                balance = serializedBlock.readAttoAmount(),
                timestamp = serializedBlock.readInstant(),
                previous = serializedBlock.readAttoHash(),
                representativeAlgorithm = serializedBlock.readAttoAlgorithm(),
                representativePublicKey = serializedBlock.readAttoPublicKey(),
            )
        }
    }

    override fun toBuffer(): Buffer =
        Buffer().apply {
            this.writeAttoBlockType(type)
            this.writeAttoNetwork(network)
            this.writeAttoVersion(version)
            this.writeAttoAlgorithm(algorithm)
            this.writeAttoPublicKey(publicKey)
            this.writeAttoHeight(height)
            this.writeAttoAmount(balance)
            this.writeInstant(timestamp)
            this.writeAttoHash(previous)
            this.writeAttoAlgorithm(representativeAlgorithm)
            this.writeAttoPublicKey(representativePublicKey)
        }

    override fun isValid(): Boolean = super.isValid() && height > AttoHeight(1u)
}
