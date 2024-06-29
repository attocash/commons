package cash.atto.commons

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class AttoBufferExtensionsTest {
    @Test
    fun test() {
        // given
        val size = 185L
        val buffer = Buffer()

        // when
        val expectedHash = AttoHash(Random.Default.nextBytes(ByteArray(32)))
        buffer.writeAttoHash(expectedHash)

        val expectedPublicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32)))
        buffer.writeAttoPublicKey(expectedPublicKey)

        val expectedVersion = UShort.MAX_VALUE.toAttoVersion()
        buffer.writeAttoVersion(expectedVersion)

        val expectedHeight = ULong.MAX_VALUE.toAttoHeight()
        buffer.writeAttoHeight(expectedHeight)

        val expectedInstant = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
        buffer.writeInstant(expectedInstant)

        val expectedBlockType = AttoBlockType.SEND
        buffer.writeAttoBlockType(expectedBlockType)

        val expectedAmount = AttoAmount.MAX
        buffer.writeAttoAmount(expectedAmount)

        val expectedSignature = AttoSignature(Random.Default.nextBytes(ByteArray(64)))
        buffer.writeAttoSignature(expectedSignature)

        val expectedWork = AttoWork(Random.Default.nextBytes(ByteArray(8)))
        buffer.writeAttoWork(expectedWork)

        val expectedSocketAddress = AttoSocketAddress(Random.nextBytes(16), 8330U)
        buffer.writeAttoSocketAddress(expectedSocketAddress)

        val expectedNetwork = AttoNetwork.LOCAL
        buffer.writeAttoNetwork(expectedNetwork)

        val expectedAlgorithm = AttoAlgorithm.V1
        buffer.writeAttoAlgorithm(expectedAlgorithm)

        // then
        assertEquals(size, buffer.size)
        assertEquals(expectedHash, buffer.readAttoHash())
        assertEquals(expectedPublicKey, buffer.readAttoPublicKey())
        assertEquals(expectedVersion, buffer.readAttoVersion())
        assertEquals(expectedHeight, buffer.readAttoHeight())
        assertEquals(expectedInstant, buffer.readInstant())
        assertEquals(expectedBlockType, buffer.readAttoBlockType())
        assertEquals(expectedAmount, buffer.readAttoAmount())
        assertEquals(expectedSignature, buffer.readAttoSignature())
        assertEquals(expectedWork, buffer.readAttoWork())
        assertEquals(expectedSocketAddress, buffer.readAttoSocketAddress())
        assertEquals(expectedNetwork, buffer.readAttoNetwork())
        assertEquals(expectedAlgorithm, buffer.readAttoAlgorithm())
    }

//    @Test
//    fun slice() {
//        // given
//        val buffer = AttoByteBuffer(6)
//        buffer.add((1).toShort())
//        buffer.add((2).toShort())
//        buffer.add((3).toShort())
//
//        // when
//        val short = buffer.slice(4).getShort()
//
//        // then
//        assertEquals(3, short)
//    }
//
//    @Test
//    fun toByteArray() {
//        // given
//        val buffer =
//            AttoByteBuffer(2)
//                .add((1u).toUByte())
//                .add((2u).toUByte())
//                .slice(1)
//
//        buffer.getUByte()
//
//        // when
//        val byteArray = buffer.toByteArray()
//
//        // then
//        assertEquals(1, byteArray.size)
//    }
}
