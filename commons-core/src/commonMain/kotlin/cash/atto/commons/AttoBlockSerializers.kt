@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object AttoSendBlockSerializer : KSerializer<AttoSendBlock> {
    override val descriptor = AttoSendBlockOutput.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoSendBlock,
    ) {
        if (encoder is JsonEncoder) {
            encoder.encodeSerializableValue(
                AttoSendBlockOutput.serializer(),
                AttoSendBlockOutput(
                    network = value.network,
                    version = value.version,
                    height = value.height,
                    balance = value.balance,
                    timestamp = value.timestamp,
                    previous = value.previous,
                    amount = value.amount,
                    address = value.address,
                    receiverAddress = value.receiverAddress,
                ),
            )
            return
        }

        encoder.encodeSerializableValue(
            AttoSendBlockBinaryWire.serializer(),
            AttoSendBlockBinaryWire(
                network = value.network,
                version = value.version,
                algorithm = value.address.algorithm,
                publicKey = value.address.publicKey,
                height = value.height,
                balance = value.balance,
                timestamp = value.timestamp,
                previous = value.previous,
                receiverAlgorithm = value.receiverAddress.algorithm,
                receiverPublicKey = value.receiverAddress.publicKey,
                amount = value.amount,
                address = value.address,
                receiverAddress = value.receiverAddress,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): AttoSendBlock =
        if (decoder is JsonDecoder) {
            decodeBlockJson(decoder, AttoSendBlockOutput.serializer()).toBlock()
        } else {
            decoder.decodeSerializableValue(AttoSendBlockBinaryWire.serializer()).toBlock()
        }
}

object AttoReceiveBlockSerializer : KSerializer<AttoReceiveBlock> {
    override val descriptor = AttoReceiveBlockOutput.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoReceiveBlock,
    ) {
        if (encoder is JsonEncoder) {
            encoder.encodeSerializableValue(
                AttoReceiveBlockOutput.serializer(),
                AttoReceiveBlockOutput(
                    network = value.network,
                    version = value.version,
                    height = value.height,
                    balance = value.balance,
                    timestamp = value.timestamp,
                    previous = value.previous,
                    sendHashAlgorithm = value.sendHashAlgorithm,
                    sendHash = value.sendHash,
                    address = value.address,
                ),
            )
            return
        }

        encoder.encodeSerializableValue(
            AttoReceiveBlockBinaryWire.serializer(),
            AttoReceiveBlockBinaryWire(
                network = value.network,
                version = value.version,
                algorithm = value.address.algorithm,
                publicKey = value.address.publicKey,
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
        if (decoder is JsonDecoder) {
            decodeBlockJson(decoder, AttoReceiveBlockOutput.serializer()).toBlock()
        } else {
            decoder.decodeSerializableValue(AttoReceiveBlockBinaryWire.serializer()).toBlock()
        }
}

object AttoOpenBlockSerializer : KSerializer<AttoOpenBlock> {
    override val descriptor = AttoOpenBlockOutput.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoOpenBlock,
    ) {
        if (encoder is JsonEncoder) {
            encoder.encodeSerializableValue(
                AttoOpenBlockOutput.serializer(),
                AttoOpenBlockOutput(
                    network = value.network,
                    version = value.version,
                    balance = value.balance,
                    timestamp = value.timestamp,
                    sendHashAlgorithm = value.sendHashAlgorithm,
                    sendHash = value.sendHash,
                    height = value.height,
                    address = value.address,
                    representativeAddress = value.representativeAddress,
                ),
            )
            return
        }

        encoder.encodeSerializableValue(
            AttoOpenBlockBinaryWire.serializer(),
            AttoOpenBlockBinaryWire(
                network = value.network,
                version = value.version,
                algorithm = value.address.algorithm,
                publicKey = value.address.publicKey,
                balance = value.balance,
                timestamp = value.timestamp,
                sendHashAlgorithm = value.sendHashAlgorithm,
                sendHash = value.sendHash,
                representativeAlgorithm = value.representativeAddress.algorithm,
                representativePublicKey = value.representativeAddress.publicKey,
                height = value.height,
                address = value.address,
                representativeAddress = value.representativeAddress,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): AttoOpenBlock =
        if (decoder is JsonDecoder) {
            decodeJson(decoder)
        } else {
            decoder.decodeSerializableValue(AttoOpenBlockBinaryWire.serializer()).toBlock()
        }

    private fun decodeJson(decoder: JsonDecoder): AttoOpenBlock {
        val element = decoder.decodeJsonElement()
        val normalized =
            if (element is JsonObject) {
                val index = descriptor.getElementIndex("height")
                val name = descriptor.getElementName(index)
                val fieldName =
                    decoder.json.configuration.namingStrategy
                        ?.serialNameForJson(descriptor, index, name) ?: name
                // OPEN height is a computed protocol constant, including on input.
                JsonObject(element + (fieldName to JsonPrimitive("1")))
            } else {
                element
            }
        return decodeBlockJson(decoder, AttoOpenBlockOutput.serializer(), normalized).toBlock()
    }
}

object AttoChangeBlockSerializer : KSerializer<AttoChangeBlock> {
    override val descriptor = AttoChangeBlockOutput.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: AttoChangeBlock,
    ) {
        if (encoder is JsonEncoder) {
            encoder.encodeSerializableValue(
                AttoChangeBlockOutput.serializer(),
                AttoChangeBlockOutput(
                    network = value.network,
                    version = value.version,
                    height = value.height,
                    balance = value.balance,
                    timestamp = value.timestamp,
                    previous = value.previous,
                    address = value.address,
                    representativeAddress = value.representativeAddress,
                ),
            )
            return
        }

        encoder.encodeSerializableValue(
            AttoChangeBlockBinaryWire.serializer(),
            AttoChangeBlockBinaryWire(
                network = value.network,
                version = value.version,
                algorithm = value.address.algorithm,
                publicKey = value.address.publicKey,
                height = value.height,
                balance = value.balance,
                timestamp = value.timestamp,
                previous = value.previous,
                representativeAlgorithm = value.representativeAddress.algorithm,
                representativePublicKey = value.representativeAddress.publicKey,
                address = value.address,
                representativeAddress = value.representativeAddress,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): AttoChangeBlock =
        if (decoder is JsonDecoder) {
            decodeBlockJson(decoder, AttoChangeBlockOutput.serializer()).toBlock()
        } else {
            decoder.decodeSerializableValue(AttoChangeBlockBinaryWire.serializer()).toBlock()
        }
}

private fun <T> decodeBlockJson(
    decoder: JsonDecoder,
    serializer: KSerializer<T>,
    element: JsonElement = decoder.decodeJsonElement(),
): T {
    if (element !is JsonObject) {
        return decoder.json.decodeFromJsonElement(serializer, element)
    }
    val descriptor = serializer.descriptor
    val naming = decoder.json.configuration.namingStrategy

    fun fieldName(index: Int): String {
        val name = descriptor.getElementName(index)
        return naming?.serialNameForJson(descriptor, index, name) ?: name
    }
    val fields = element.toMutableMap()
    val discriminator = decoder.json.configuration.classDiscriminator
    if ((0 until descriptor.elementsCount).none { fieldName(it) == discriminator }) {
        fields.remove(discriminator)
    }
    return decoder.json.decodeFromJsonElement(serializer, JsonObject(fields))
}

@Serializable
@SerialName("SEND")
private class AttoSendBlockOutput(
    val network: AttoNetwork,
    val version: AttoVersion,
    val height: AttoHeight,
    val balance: AttoAmount,
    val timestamp: AttoInstant,
    val previous: AttoHash,
    val amount: AttoAmount,
    val address: AttoAddress,
    val receiverAddress: AttoAddress,
) {
    fun toBlock(): AttoSendBlock =
        AttoSendBlock(
            network = network,
            version = version,
            address = address,
            height = height,
            balance = balance,
            timestamp = timestamp,
            previous = previous,
            receiverAddress = receiverAddress,
            amount = amount,
        )
}

@Serializable
@SerialName("RECEIVE")
private class AttoReceiveBlockOutput(
    val network: AttoNetwork,
    val version: AttoVersion,
    val height: AttoHeight,
    val balance: AttoAmount,
    val timestamp: AttoInstant,
    val previous: AttoHash,
    val sendHashAlgorithm: AttoAlgorithm,
    val sendHash: AttoHash,
    val address: AttoAddress,
) {
    fun toBlock(): AttoReceiveBlock =
        AttoReceiveBlock(
            network = network,
            version = version,
            address = address,
            height = height,
            balance = balance,
            timestamp = timestamp,
            previous = previous,
            sendHashAlgorithm = sendHashAlgorithm,
            sendHash = sendHash,
        )
}

@Serializable
@SerialName("OPEN")
private class AttoOpenBlockOutput(
    val network: AttoNetwork,
    val version: AttoVersion,
    val balance: AttoAmount,
    val timestamp: AttoInstant,
    val sendHashAlgorithm: AttoAlgorithm,
    val sendHash: AttoHash,
    val height: AttoHeight,
    val address: AttoAddress,
    val representativeAddress: AttoAddress,
) {
    fun toBlock(): AttoOpenBlock =
        AttoOpenBlock(
            network = network,
            version = version,
            address = address,
            balance = balance,
            timestamp = timestamp,
            sendHashAlgorithm = sendHashAlgorithm,
            sendHash = sendHash,
            representativeAddress =
            representativeAddress,
        )
}

@Serializable
@SerialName("CHANGE")
private class AttoChangeBlockOutput(
    val network: AttoNetwork,
    val version: AttoVersion,
    val height: AttoHeight,
    val balance: AttoAmount,
    val timestamp: AttoInstant,
    val previous: AttoHash,
    val address: AttoAddress,
    val representativeAddress: AttoAddress,
) {
    fun toBlock(): AttoChangeBlock =
        AttoChangeBlock(
            network = network,
            version = version,
            address = address,
            height = height,
            balance = balance,
            timestamp = timestamp,
            previous = previous,
            representativeAddress =
            representativeAddress,
        )
}

@Serializable
@SerialName("AttoSendBlockWire")
private data class AttoSendBlockBinaryWire(
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
private data class AttoReceiveBlockBinaryWire(
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
private data class AttoOpenBlockBinaryWire(
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
private data class AttoChangeBlockBinaryWire(
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
