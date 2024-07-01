@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AttoTransactionTest {
    val privateKey = AttoPrivateKey.generate()
    val publicKey = privateKey.toPublicKey()

    val receiveBlock =
        AttoReceiveBlock(
            version = 0U.toAttoVersion(),
            network = AttoNetwork.LOCAL,
            algorithm = AttoAlgorithm.V1,
            publicKey = publicKey,
            height = 2U.toAttoHeight(),
            balance = AttoAmount.MAX,
            timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
            previous = AttoHash(Random.nextBytes(ByteArray(32))),
            sendHashAlgorithm = AttoAlgorithm.V1,
            sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32))),
        )

    val expectedTransaction =
        AttoTransaction(
            block = receiveBlock,
            signature = privateKey.sign(receiveBlock.hash),
            work = AttoWork.work(receiveBlock),
        )

    @Test
    fun `should serialize buffer`() {
        // when
        val buffer = expectedTransaction.toBuffer()
        val transaction = AttoTransaction.fromBuffer(buffer)

        // then
        assertEquals(expectedTransaction, transaction)
    }

    @Test
    fun `should serialize json`() {
        // when
        val json = Json.encodeToString(expectedTransaction)
        val transaction = Json.decodeFromString<AttoTransaction>(json)

        // then
        assertEquals(expectedTransaction, transaction)
    }

    @Test
    fun `should serialize protobuf`() {
        // when
        val protobuf = ProtoBuf.encodeToByteArray(expectedTransaction)
        val transaction = ProtoBuf.decodeFromByteArray<AttoTransaction>(protobuf)

        // then
        assertEquals(expectedTransaction, transaction)
    }
}
