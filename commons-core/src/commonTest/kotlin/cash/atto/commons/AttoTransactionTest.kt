package cash.atto.commons

import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.cpu
import kotlinx.coroutines.test.runTest
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
            timestamp = AttoInstant.now(),
            previous = AttoHash(Random.nextBytes(ByteArray(32))),
            sendHashAlgorithm = AttoAlgorithm.V1,
            sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32))),
        )

    @Test
    fun `should serialize buffer`() =
        runTest {
            // given
            val expectedTransaction =
                AttoTransaction(
                    block = receiveBlock,
                    signature = privateKey.sign(receiveBlock.hash),
                    work = AttoWorker.cpu().work(receiveBlock),
                )

            // when
            val buffer = expectedTransaction.toBuffer()
            val transaction = AttoTransaction.fromBuffer(buffer)

            // then
            assertEquals(expectedTransaction, transaction)
        }

    @Test
    fun `should serialize json`() =
        runTest {
            // given
            val expectedTransaction =
                AttoTransaction(
                    block = receiveBlock,
                    signature = privateKey.sign(receiveBlock.hash),
                    work = AttoWorker.cpu().work(receiveBlock),
                )

            // when
            val json = Json.encodeToString(expectedTransaction)
            val transaction = Json.decodeFromString<AttoTransaction>(json)

            // then
            assertEquals(expectedTransaction, transaction)
        }
}
