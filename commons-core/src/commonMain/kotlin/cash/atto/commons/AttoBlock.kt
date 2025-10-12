@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.io.Buffer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Duration.Companion.minutes

val maxVersion = AttoVersion(0U)

@JsExportForJs
enum class AttoBlockType(
    val code: UByte,
    val size: Int,
) {
    UNKNOWN(UByte.MAX_VALUE, 0),

    OPEN(0u, 119),

    RECEIVE(1u, 126),

    SEND(2u, 134),

    CHANGE(3u, 126),
    ;

    companion object {
        private val map = entries.associateBy(AttoBlockType::code)

        fun from(code: UByte): AttoBlockType {
            return map[code] ?: UNKNOWN
        }
    }
}

@JsExportForJs
interface HeightSupport {
    val height: AttoHeight
}

@JsExportForJs
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
    val timestamp: AttoInstant

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


    fun validate(): AttoValidation {
        if (version > maxVersion) {
            return AttoValidation.Error("Invalid version: version=$version > max=$maxVersion")
        }

        val now = AttoInstant.now()
        if (timestamp > now + 1.minutes) {
            return AttoValidation.Error(
                "Timestamp too far in the future: timestamp=$timestamp, now=$now, tolerance=1m"
            )
        }

        if (algorithm.publicKeySize != publicKey.value.size) {
            return AttoValidation.Error(
                "Public key size does not match algorithm: algorithm.publicKeySize=${algorithm.publicKeySize}, publicKey.size=${publicKey.value.size}"
            )
        }

        if (this is PreviousSupport && previous.value.size != algorithm.hashSize) {
            return AttoValidation.Error(
                "Previous hash size does not match algorithm: algorithm.hashSize=${algorithm.hashSize}, previous.size=${previous.value.size}"
            )
        }

        if (this is ReceiveSupport && sendHash.value.size != sendHashAlgorithm.hashSize) {
            return AttoValidation.Error(
                "Send hash size does not match algorithm: sendHash.size=${sendHash.value.size}, sendHashAlgorithm.hashSize=${sendHashAlgorithm.hashSize}"
            )
        }

        if (this is ReceiveSupport && balance == AttoAmount.MIN) {
            return AttoValidation.Error(
                "Balance must be greater than 0"
            )
        }

        if (this !is AttoOpenBlock && height <= AttoHeight.MIN) {
            return AttoValidation.Error("Height must be greater than 1: height=$height")
        }

        if (this is RepresentativeSupport &&
            representativeAlgorithm.publicKeySize != representativePublicKey.value.size
        ) {
            return AttoValidation.Error(
                "Representative public key size does not match representative algorithm: representativeAlgorithm.publicKeySize=${representativeAlgorithm.publicKeySize}, representativePublicKey.size=${representativePublicKey.value.size}"
            )
        }

        return AttoValidation.Ok
    }

    fun isValid(): Boolean = validate().isValid
}

@JsExportForJs
interface PreviousSupport {
    val height: AttoHeight
    val previous: AttoHash
}

@JsExportForJs
interface ReceiveSupport {
    val sendHashAlgorithm: AttoAlgorithm
    val sendHash: AttoHash
}

@JsExportForJs
interface RepresentativeSupport {
    val representativeAlgorithm: AttoAlgorithm
    val representativePublicKey: AttoPublicKey
}

@JsExportForJs
@Serializable
@SerialName("SEND")
data class AttoSendBlock(
    override val network: AttoNetwork,
    override val version: AttoVersion,
    override val algorithm: AttoAlgorithm,
    override val publicKey: AttoPublicKey,
    override val height: AttoHeight,
    override val balance: AttoAmount,
    override val timestamp: AttoInstant,
    override val previous: AttoHash,
    val receiverAlgorithm: AttoAlgorithm,
    val receiverPublicKey: AttoPublicKey,
    val amount: AttoAmount,
) : AttoBlock,
    PreviousSupport {
    @Transient
    override val type = AttoBlockType.SEND

    override val hash by lazy { toBuffer().hash() }

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val address = AttoAddress(algorithm, publicKey)

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val receiverAddress = AttoAddress(receiverAlgorithm, receiverPublicKey)

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

    override fun validate(): AttoValidation {
        when (val parent = super.validate()) {
            is AttoValidation.Ok -> Unit
            is AttoValidation.Error -> return parent
        }

        if (amount == AttoAmount.MIN) {
            return AttoValidation.Error("Amount must be greater than ${AttoAmount.MIN}")
        }

        if (receiverPublicKey == publicKey) {
            return AttoValidation.Error(
                "Receiver public key must be different from public key: receiverPublicKey=$receiverPublicKey, publicKey=$publicKey"
            )
        }

        if (receiverAlgorithm.publicKeySize != receiverPublicKey.value.size) {
            return AttoValidation.Error(
                "Receiver public key size does not match receiver algorithm: receiverAlgorithm.publicKeySize=${receiverAlgorithm.publicKeySize}, receiverPublicKey.size=${receiverPublicKey.value.size}"
            )
        }

        return AttoValidation.Ok
    }
}

@JsExportForJs
@Serializable
@SerialName("RECEIVE")
data class AttoReceiveBlock(
    override val network: AttoNetwork,
    override val version: AttoVersion,
    override val algorithm: AttoAlgorithm,
    override val publicKey: AttoPublicKey,
    override val height: AttoHeight,
    override val balance: AttoAmount,
    override val timestamp: AttoInstant,
    override val previous: AttoHash,
    override val sendHashAlgorithm: AttoAlgorithm,
    override val sendHash: AttoHash,
) : AttoBlock,
    PreviousSupport,
    ReceiveSupport {
    @Transient
    override val type = AttoBlockType.RECEIVE

    override val hash by lazy { toBuffer().hash() }

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val address = AttoAddress(algorithm, publicKey)

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
}

@JsExportForJs
@Serializable
@SerialName("OPEN")
data class AttoOpenBlock(
    override val network: AttoNetwork,
    override val version: AttoVersion,
    override val algorithm: AttoAlgorithm,
    override val publicKey: AttoPublicKey,
    override val balance: AttoAmount,
    override val timestamp: AttoInstant,
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

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val address = AttoAddress(algorithm, publicKey)

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val representativeAddress = AttoAddress(representativeAlgorithm, representativePublicKey)

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
}

@JsExportForJs
@Serializable
@SerialName("CHANGE")
data class AttoChangeBlock(
    override val network: AttoNetwork,
    override val version: AttoVersion,
    override val algorithm: AttoAlgorithm,
    override val publicKey: AttoPublicKey,
    override val height: AttoHeight,
    override val balance: AttoAmount,
    override val timestamp: AttoInstant,
    override val previous: AttoHash,
    override val representativeAlgorithm: AttoAlgorithm,
    override val representativePublicKey: AttoPublicKey,
) : AttoBlock,
    PreviousSupport,
    RepresentativeSupport {
    @Transient
    override val type = AttoBlockType.CHANGE

    override val hash by lazy { toBuffer().hash() }

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val address = AttoAddress(algorithm, publicKey)

    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val representativeAddress = AttoAddress(representativeAlgorithm, representativePublicKey)

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
}
