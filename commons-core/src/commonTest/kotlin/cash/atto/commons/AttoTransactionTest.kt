package cash.atto.commons

import cash.atto.commons.work.AttoWorker
import cash.atto.commons.work.cpu
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

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
            work = runBlocking { AttoWorker.cpu().work(receiveBlock) },
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
}
