@file:JvmName("AttoAddresses")

package cash.atto.commons

import cash.atto.commons.utils.Base32
import cash.atto.commons.utils.JsExportForJs
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeUByte
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmName

private const val SCHEMA = "atto://"

private fun ByteArray.toAddressPath(): String {
    require(this.size == 38) { "ByteArray should have 38 bytes" }
    return Base32.encode(this).replace("=", "").lowercase()
}

private fun String.fromAddress(): ByteArray = Base32.decode(this.substring(SCHEMA.length).uppercase() + "===")

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
@Serializable(with = AttoAddressAsStringSerializer::class)
data class AttoAddress(
    val algorithm: AttoAlgorithm,
    val publicKey: AttoPublicKey,
) {
    val schema = SCHEMA
    val path = toAddressPath(algorithm, publicKey)
    val value = schema + path

    companion object {
        private val regex = "^$SCHEMA[a-z2-7]{61}$".toRegex()
        private const val CHECKSUM_SIZE = 5

        private fun checksum(
            algorithm: ByteArray,
            publicKey: ByteArray,
        ): ByteArray =
            AttoHasher.hash(
                CHECKSUM_SIZE,
                algorithm,
                publicKey,
            )

        private fun checksum(
            algorithm: AttoAlgorithm,
            publicKey: AttoPublicKey,
        ): ByteArray = checksum(byteArrayOf(algorithm.code.toByte()), publicKey.value)

        private fun toAlgorithmPublicKey(decoded: ByteArray): Pair<AttoAlgorithm, AttoPublicKey> {
            val algorithm = AttoAlgorithm.from(decoded[0].toUByte())
            val publicKey = AttoPublicKey(decoded.sliceArray(1 until 33))

            return algorithm to publicKey
        }

        private fun toAlgorithmPublicKey(value: String): Pair<AttoAlgorithm, AttoPublicKey> = toAlgorithmPublicKey(value.fromAddress())

        fun isValid(value: String): Boolean {
            if (!regex.matches(value)) {
                return false
            }
            val decoded = value.fromAddress()

            val (algorithm, publicKey) = toAlgorithmPublicKey(decoded)
            val checksum = decoded.sliceArray(33 until decoded.size)

            return checksum.contentEquals(checksum(algorithm, publicKey))
        }

        fun isValidPath(path: String): Boolean {
            val value = SCHEMA + path
            return isValid(value)
        }

        fun toAddressPath(
            algorithm: AttoAlgorithm,
            publicKey: AttoPublicKey,
        ): String {
            val algorithm = byteArrayOf(algorithm.code.toByte())
            val publicKey = publicKey.value
            val checksum = checksum(algorithm, publicKey)

            return (algorithm + publicKey + checksum).toAddressPath()
        }

        @JsName("parseSerialized")
        fun parse(serialized: ByteArray): AttoAddress {
            val algorithm = AttoAlgorithm.from(serialized[0].toUByte())
            val publicKey = AttoPublicKey(serialized.sliceArray(1 until serialized.size))
            return AttoAddress(algorithm, publicKey)
        }

        @OptIn(ExperimentalJsStatic::class)
        @JsStatic
        fun parse(value: String): AttoAddress {
            val address = if (value.startsWith(SCHEMA)) value else SCHEMA + value
            require(isValid(address)) { "$value is invalid" }

            val (algorithm, publicKey) = toAlgorithmPublicKey(address)

            return AttoAddress(algorithm, publicKey)
        }

        @Deprecated("Use parse instead", ReplaceWith("parse(path)"))
        fun parsePath(path: String): AttoAddress = parse(path)
    }

    @JsExport.Ignore
    fun toBuffer(): Buffer =
        Buffer().apply {
            this.writeUByte(algorithm.code)
            this.write(publicKey.value, 0, publicKey.value.size)
        }

    override fun toString(): String = value
}

@JsExportForJs
fun AttoPublicKey.toAddress(algorithm: AttoAlgorithm): AttoAddress = AttoAddress(algorithm, this)

object AttoAddressAsStringSerializer : KSerializer<AttoAddress> {
    override val descriptor = PrimitiveSerialDescriptor("AttoAddressAsString", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoAddress,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoAddress = AttoAddress.parse(decoder.decodeString())
}

object AttoAddressAsPathStringSerializer : KSerializer<AttoAddress> {
    override val descriptor = PrimitiveSerialDescriptor("AttoAddressAsPathString", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoAddress,
    ) {
        encoder.encodeString(value.path)
    }

    override fun deserialize(decoder: Decoder): AttoAddress = AttoAddress.parse(decoder.decodeString())
}

object AttoAddressAsByteArraySerializer : KSerializer<AttoAddress> {
    override val descriptor = PrimitiveSerialDescriptor("AttoAddressAsByteArray", PrimitiveKind.BYTE)

    override fun serialize(
        encoder: Encoder,
        value: AttoAddress,
    ) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.toBuffer().readByteArray())
    }

    override fun deserialize(decoder: Decoder): AttoAddress = AttoAddress.parse(decoder.decodeSerializableValue(ByteArraySerializer()))
}
