@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AttoSendBlockSerializer : KSerializer<AttoSendBlock> {
    override val descriptor = SerialDescriptor("SEND", AttoSendBlockWire.serializer().descriptor)

    override fun serialize(
        encoder: Encoder,
        value: AttoSendBlock,
    ) {
        encoder.encodeSerializableValue(
            AttoSendBlockWire.serializer(),
            AttoSendBlockWire(
                network = value.network,
                version = value.version,
                algorithm = value.algorithm,
                publicKey = value.publicKey,
                height = value.height,
                balance = value.balance,
                timestamp = value.timestamp,
                previous = value.previous,
                receiverAlgorithm = value.receiverAlgorithm,
                receiverPublicKey = value.receiverPublicKey,
                amount = value.amount,
                address = value.address,
                receiverAddress = value.receiverAddress,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): AttoSendBlock =
        decodeWithSerializedProjections(
            decoder,
            AttoSendBlockWire.serializer(),
            mapOf(
                "address" to listOf("algorithm", "publicKey"),
                "receiverAddress" to listOf("receiverAlgorithm", "receiverPublicKey"),
            ),
        ).toBlock()
}

object AttoReceiveBlockSerializer : KSerializer<AttoReceiveBlock> {
    override val descriptor = SerialDescriptor("RECEIVE", AttoReceiveBlockWire.serializer().descriptor)

    override fun serialize(
        encoder: Encoder,
        value: AttoReceiveBlock,
    ) {
        encoder.encodeSerializableValue(
            AttoReceiveBlockWire.serializer(),
            AttoReceiveBlockWire(
                network = value.network,
                version = value.version,
                algorithm = value.algorithm,
                publicKey = value.publicKey,
                height = value.height,
                balance = value.balance,
                timestamp = value.timestamp,
                previous = value.previous,
                sendHashAlgorithm = value.sendHashAlgorithm,
                sendHash = value.sendHash,
                address = value.address,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): AttoReceiveBlock =
        decodeWithSerializedProjections(
            decoder,
            AttoReceiveBlockWire.serializer(),
            mapOf("address" to listOf("algorithm", "publicKey")),
        ).toBlock()
}

object AttoOpenBlockSerializer : KSerializer<AttoOpenBlock> {
    override val descriptor = SerialDescriptor("OPEN", AttoOpenBlockWire.serializer().descriptor)

    override fun serialize(
        encoder: Encoder,
        value: AttoOpenBlock,
    ) {
        encoder.encodeSerializableValue(
            AttoOpenBlockWire.serializer(),
            AttoOpenBlockWire(
                network = value.network,
                version = value.version,
                algorithm = value.algorithm,
                publicKey = value.publicKey,
                balance = value.balance,
                timestamp = value.timestamp,
                sendHashAlgorithm = value.sendHashAlgorithm,
                sendHash = value.sendHash,
                representativeAlgorithm = value.representativeAlgorithm,
                representativePublicKey = value.representativePublicKey,
                height = value.height,
                address = value.address,
                representativeAddress = value.representativeAddress,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): AttoOpenBlock =
        decodeWithSerializedProjections(
            decoder,
            AttoOpenBlockWire.serializer(),
            mapOf(
                "address" to listOf("algorithm", "publicKey"),
                "representativeAddress" to listOf("representativeAlgorithm", "representativePublicKey"),
            ),
            ignoredFields = setOf("height"),
        ).toBlock()
}

object AttoChangeBlockSerializer : KSerializer<AttoChangeBlock> {
    override val descriptor = SerialDescriptor("CHANGE", AttoChangeBlockWire.serializer().descriptor)

    override fun serialize(
        encoder: Encoder,
        value: AttoChangeBlock,
    ) {
        encoder.encodeSerializableValue(
            AttoChangeBlockWire.serializer(),
            AttoChangeBlockWire(
                network = value.network,
                version = value.version,
                algorithm = value.algorithm,
                publicKey = value.publicKey,
                height = value.height,
                balance = value.balance,
                timestamp = value.timestamp,
                previous = value.previous,
                representativeAlgorithm = value.representativeAlgorithm,
                representativePublicKey = value.representativePublicKey,
                address = value.address,
                representativeAddress = value.representativeAddress,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): AttoChangeBlock =
        decodeWithSerializedProjections(
            decoder,
            AttoChangeBlockWire.serializer(),
            mapOf(
                "address" to listOf("algorithm", "publicKey"),
                "representativeAddress" to listOf("representativeAlgorithm", "representativePublicKey"),
            ),
        ).toBlock()
}

@Serializable
@SerialName("AttoSendBlockWire")
private data class AttoSendBlockWire(
    val network: AttoNetwork,
    val version: AttoVersion,
    val algorithm: AttoAlgorithm? = null,
    val publicKey: AttoPublicKey? = null,
    val height: AttoHeight,
    val balance: AttoAmount,
    val timestamp: AttoInstant,
    val previous: AttoHash,
    val receiverAlgorithm: AttoAlgorithm? = null,
    val receiverPublicKey: AttoPublicKey? = null,
    val amount: AttoAmount,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val address: AttoAddress? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val receiverAddress: AttoAddress? = null,
) {
    fun toBlock(): AttoSendBlock =
        AttoSendBlock(
            network = network,
            version = version,
            address = resolveSerializedAddress("address", address, algorithm, publicKey),
            height = height,
            balance = balance,
            timestamp = timestamp,
            previous = previous,
            receiverAddress = resolveSerializedAddress("receiverAddress", receiverAddress, receiverAlgorithm, receiverPublicKey),
            amount = amount,
        )
}

@Serializable
@SerialName("AttoReceiveBlockWire")
private data class AttoReceiveBlockWire(
    val network: AttoNetwork,
    val version: AttoVersion,
    val algorithm: AttoAlgorithm? = null,
    val publicKey: AttoPublicKey? = null,
    val height: AttoHeight,
    val balance: AttoAmount,
    val timestamp: AttoInstant,
    val previous: AttoHash,
    val sendHashAlgorithm: AttoAlgorithm,
    val sendHash: AttoHash,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val address: AttoAddress? = null,
) {
    fun toBlock(): AttoReceiveBlock =
        AttoReceiveBlock(
            network = network,
            version = version,
            address = resolveSerializedAddress("address", address, algorithm, publicKey),
            height = height,
            balance = balance,
            timestamp = timestamp,
            previous = previous,
            sendHashAlgorithm = sendHashAlgorithm,
            sendHash = sendHash,
        )
}

@Serializable
@SerialName("AttoOpenBlockWire")
private data class AttoOpenBlockWire(
    val network: AttoNetwork,
    val version: AttoVersion,
    val algorithm: AttoAlgorithm? = null,
    val publicKey: AttoPublicKey? = null,
    val balance: AttoAmount,
    val timestamp: AttoInstant,
    val sendHashAlgorithm: AttoAlgorithm,
    val sendHash: AttoHash,
    val representativeAlgorithm: AttoAlgorithm? = null,
    val representativePublicKey: AttoPublicKey? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val height: AttoHeight = AttoHeight.MIN,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val address: AttoAddress? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val representativeAddress: AttoAddress? = null,
) {
    fun toBlock(): AttoOpenBlock =
        AttoOpenBlock(
            network = network,
            version = version,
            address = resolveSerializedAddress("address", address, algorithm, publicKey),
            balance = balance,
            timestamp = timestamp,
            sendHashAlgorithm = sendHashAlgorithm,
            sendHash = sendHash,
            representativeAddress =
                resolveSerializedAddress("representativeAddress", representativeAddress, representativeAlgorithm, representativePublicKey),
        )
}

@Serializable
@SerialName("AttoChangeBlockWire")
private data class AttoChangeBlockWire(
    val network: AttoNetwork,
    val version: AttoVersion,
    val algorithm: AttoAlgorithm? = null,
    val publicKey: AttoPublicKey? = null,
    val height: AttoHeight,
    val balance: AttoAmount,
    val timestamp: AttoInstant,
    val previous: AttoHash,
    val representativeAlgorithm: AttoAlgorithm? = null,
    val representativePublicKey: AttoPublicKey? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val address: AttoAddress? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val representativeAddress: AttoAddress? = null,
) {
    fun toBlock(): AttoChangeBlock =
        AttoChangeBlock(
            network = network,
            version = version,
            address = resolveSerializedAddress("address", address, algorithm, publicKey),
            height = height,
            balance = balance,
            timestamp = timestamp,
            previous = previous,
            representativeAddress =
                resolveSerializedAddress("representativeAddress", representativeAddress, representativeAlgorithm, representativePublicKey),
        )
}
