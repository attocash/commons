@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import cash.atto.commons.serialiazers.json.AttoJson
import cash.atto.commons.serialiazers.protobuf.AttoProtobuf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AttoTransactionTest {
    val privateKey = AttoPrivateKey.generate()
    val publicKey = privateKey.toPublicKey()

    val receiveBlock = AttoReceiveBlock(
        version = 0U,
        algorithm = AttoAlgorithm.V1,
        publicKey = publicKey,
        height = 2U,
        balance = AttoAmount.MAX,
        timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
        previous = AttoHash(Random.nextBytes(ByteArray(32))),
        sendHashAlgorithm = AttoAlgorithm.V1,
        sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32)))
    )

    val expectedTransaction = AttoTransaction(
        block = receiveBlock,
        signature = privateKey.sign(receiveBlock.hash),
        work = AttoWork.work(AttoNetwork.LOCAL, receiveBlock.timestamp, receiveBlock.previous)
    )

    @Test
    fun `should serialize byteBuffer`() {
        // when
        val byteBuffer = expectedTransaction.toByteBuffer()
        val transaction = AttoTransaction.fromByteBuffer(AttoNetwork.LOCAL, byteBuffer)

        // then
        assertEquals(expectedTransaction, transaction)
    }

    @Test
    fun `should serialize json`() {
        // when
        val json = AttoJson.encodeToString(expectedTransaction)
        val transaction = AttoJson.decodeFromString<AttoTransaction>(json)

        // then
        assertEquals(expectedTransaction, transaction)
    }

    @Test
    fun `should serialize protobuf`() {
        // when
        val protobuf = AttoProtobuf.encodeToByteArray(expectedTransaction)
        val transaction = AttoProtobuf.decodeFromByteArray<AttoTransaction>(protobuf)

        // then
        assertEquals(expectedTransaction, transaction)
    }

}