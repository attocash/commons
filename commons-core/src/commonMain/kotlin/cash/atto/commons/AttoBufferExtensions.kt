package cash.atto.commons

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readLongLe
import kotlinx.io.readUByte
import kotlinx.io.readULongLe
import kotlinx.io.readUShortLe
import kotlinx.io.writeLongLe
import kotlinx.io.writeUByte
import kotlinx.io.writeULongLe
import kotlinx.io.writeUShortLe

fun Buffer.hash(): AttoHash = AttoHash.hash(32, this.copy().readByteArray())

fun Buffer.copy(
    start: Long = 0L,
    end: Long = this.size,
): Buffer =
    Buffer().apply {
        copyTo(this, start, end)
    }

fun Buffer.writeAttoBlockType(blockType: AttoBlockType): Buffer {
    this.writeUByte(blockType.code)
    return this
}

fun Buffer.readAttoBlockType(): AttoBlockType = AttoBlockType.from(this.readUByte())

fun Buffer.writeAttoAlgorithm(algorithm: AttoAlgorithm): Buffer {
    this.writeUByte(algorithm.code)
    return this
}

fun Buffer.readAttoAlgorithm(): AttoAlgorithm = AttoAlgorithm.from(this.readUByte())

fun Buffer.writeAttoPublicKey(publicKey: AttoPublicKey): Buffer {
    this.write(publicKey.value)
    return this
}

fun Buffer.readAttoPublicKey(): AttoPublicKey = AttoPublicKey(this.readByteArray(32))

fun Buffer.writeAttoAmount(amount: AttoAmount): Buffer {
    this.writeULongLe(amount.raw)
    return this
}

fun Buffer.readAttoAmount(): AttoAmount = AttoAmount(this.readULongLe())

fun Buffer.writeInstant(instant: AttoInstant): Buffer {
    this.writeLongLe(instant.toEpochMilliseconds())
    return this
}

fun Buffer.readInstant(): AttoInstant = AttoInstant.fromEpochMilliseconds(this.readLongLe())

fun Buffer.writeAttoHash(hash: AttoHash): Buffer {
    this.write(hash.value)
    return this
}

fun Buffer.readAttoHash(): AttoHash = AttoHash(this.readByteArray(32))

fun Buffer.writeAttoVersion(version: AttoVersion): Buffer {
    this.writeUShortLe(version.value)
    return this
}

fun Buffer.readAttoVersion(): AttoVersion = AttoVersion(this.readUShortLe())

fun Buffer.writeAttoHeight(height: AttoHeight): Buffer {
    this.writeULongLe(height.value)
    return this
}

fun Buffer.readAttoHeight(): AttoHeight = AttoHeight(this.readULongLe())

fun Buffer.writeAttoSignature(signature: AttoSignature): Buffer {
    this.write(signature.value)
    return this
}

fun Buffer.readAttoSignature(): AttoSignature = AttoSignature(this.readByteArray(64))

fun Buffer.writeAttoWork(signature: AttoWork): Buffer {
    this.write(signature.value)
    return this
}

fun Buffer.readAttoWork(): AttoWork = AttoWork(this.readByteArray(8))

fun Buffer.writeAttoSocketAddress(socketAddress: AttoSocketAddress): Buffer {
    this.write(socketAddress.address)
    this.writeUShortLe(socketAddress.port)
    return this
}

fun Buffer.readAttoSocketAddress(): AttoSocketAddress = AttoSocketAddress(this.readByteArray(16), this.readUShortLe())

fun Buffer.writeAttoNetwork(network: AttoNetwork): Buffer {
    this.writeUByte(network.code)
    return this
}

fun Buffer.readAttoNetwork(): AttoNetwork = AttoNetwork.from(this.readUByte())

fun ByteArray.toBuffer(): Buffer {
    val buffer = Buffer()
    buffer.write(this)
    return buffer
}
