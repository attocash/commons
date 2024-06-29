package cash.atto.commons

import kotlinx.datetime.Instant
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

fun Buffer.hash(): AttoHash {
    return AttoHash.hash(32, this.copy().readByteArray())
}

fun Buffer.copy(
    start: Long = 0L,
    end: Long = this.size,
): Buffer {
    return Buffer().apply {
        copyTo(this, start, end)
    }
}

fun Buffer.writeAttoBlockType(blockType: AttoBlockType): Buffer {
    this.writeUByte(blockType.code)
    return this
}

fun Buffer.readAttoBlockType(): AttoBlockType {
    return AttoBlockType.from(this.readUByte())
}

fun Buffer.writeAttoAlgorithm(algorithm: AttoAlgorithm): Buffer {
    this.writeUByte(algorithm.code)
    return this
}

fun Buffer.readAttoAlgorithm(): AttoAlgorithm {
    return AttoAlgorithm.from(this.readUByte())
}

fun Buffer.writeAttoPublicKey(publicKey: AttoPublicKey): Buffer {
    this.write(publicKey.value)
    return this
}

fun Buffer.readAttoPublicKey(): AttoPublicKey {
    return AttoPublicKey(this.readByteArray(32))
}

fun Buffer.writeAttoAmount(amount: AttoAmount): Buffer {
    this.writeULongLe(amount.raw)
    return this
}

fun Buffer.readAttoAmount(): AttoAmount {
    return AttoAmount(this.readULongLe())
}

fun Buffer.writeInstant(instant: Instant): Buffer {
    this.writeLongLe(instant.toEpochMilliseconds())
    return this
}

fun Buffer.readInstant(): Instant {
    return Instant.fromEpochMilliseconds(this.readLongLe())
}

fun Buffer.writeAttoHash(hash: AttoHash): Buffer {
    this.write(hash.value)
    return this
}

fun Buffer.readAttoHash(): AttoHash {
    return AttoHash(this.readByteArray(32))
}

fun Buffer.writeAttoVersion(version: AttoVersion): Buffer {
    this.writeUShortLe(version.value)
    return this
}

fun Buffer.readAttoVersion(): AttoVersion {
    return AttoVersion(this.readUShortLe())
}

fun Buffer.writeAttoHeight(height: AttoHeight): Buffer {
    this.writeULongLe(height.value)
    return this
}

fun Buffer.readAttoHeight(): AttoHeight {
    return AttoHeight(this.readULongLe())
}

fun Buffer.writeAttoSignature(signature: AttoSignature): Buffer {
    this.write(signature.value)
    return this
}

fun Buffer.readAttoSignature(): AttoSignature {
    return AttoSignature(this.readByteArray(64))
}

fun Buffer.writeAttoWork(signature: AttoWork): Buffer {
    this.write(signature.value)
    return this
}

fun Buffer.readAttoWork(): AttoWork {
    return AttoWork(this.readByteArray(8))
}

fun Buffer.writeAttoSocketAddress(socketAddress: AttoSocketAddress): Buffer {
    this.write(socketAddress.address)
    this.writeUShortLe(socketAddress.port)
    return this
}

fun Buffer.readAttoSocketAddress(): AttoSocketAddress {
    return AttoSocketAddress(this.readByteArray(16), this.readUShortLe())
}

fun Buffer.writeAttoNetwork(network: AttoNetwork): Buffer {
    this.write(network.environment.toByteArray(Charsets.UTF_8))
    return this
}

fun Buffer.readAttoNetwork(): AttoNetwork = AttoNetwork.from(this.readByteArray(3).toString(Charsets.UTF_8))

fun ByteArray.toBuffer(): Buffer {
    val buffer = Buffer()
    buffer.write(this)
    return buffer
}
