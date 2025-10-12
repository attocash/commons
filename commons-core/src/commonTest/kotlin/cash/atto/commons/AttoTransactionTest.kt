package cash.atto.commons

import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.cpu
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `should validate`() =
        runTest {
            // given
            val transaction =
                AttoTransaction(
                    block = receiveBlock,
                    signature = privateKey.sign(receiveBlock.hash),
                    work = AttoWorker.cpu().work(receiveBlock),
                )

            assertTrue { transaction.isValid() }
        }

    @Test
    fun `should return error when block is invalid`() =
        runTest {
            // given
            val transaction =
                AttoTransaction(
                    block = receiveBlock.copy(version = UShort.MAX_VALUE.toAttoVersion()),
                    signature = privateKey.sign(receiveBlock.hash),
                    work = AttoWorker.cpu().work(receiveBlock),
                )

            assertFalse { transaction.isValid() }
            val error = transaction.validate().getError()!!
            assertTrue(error) { error.startsWith("Invalid version: version=65535 > max=0") }
        }

    @Test
    fun `should return error when work is invalid`() =
        runTest {
            // given
            val invalidWork =
                run {
                    var work: AttoWork
                    do {
                        work = AttoWork(Random.nextBytes(8))
                    } while (work.isValid(receiveBlock))
                    return@run work
                }
            val transaction =
                AttoTransaction(
                    block = receiveBlock,
                    signature = privateKey.sign(receiveBlock.hash),
                    work = invalidWork,
                )

            assertFalse { transaction.isValid() }
            val error = transaction.validate().getError()!!
            assertTrue(error) { error.startsWith("Work is invalid") }
        }

    @Test
    fun `should return error when signature is invalid`() =
        runTest {
            // given
            val invalidSignature = AttoSignature(Random.nextBytes(64))
            val transaction =
                AttoTransaction(
                    block = receiveBlock,
                    signature = invalidSignature,
                    work = AttoWorker.cpu().work(receiveBlock),
                )

            assertFalse { transaction.isValid() }
            val error = transaction.validate().getError()!!
            assertTrue(error) { error.startsWith("Signature is invalid") }
        }

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

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `should serialize protobuf`() =
        runTest {
            // given
            val expectedTransaction =
                AttoTransaction(
                    block = receiveBlock,
                    signature = privateKey.sign(receiveBlock.hash),
                    work = AttoWorker.cpu().work(receiveBlock),
                )
            val expectedWrapper = ProtobufWrapper(expectedTransaction)

            // when
            val protobuf = ProtoBuf.encodeToByteArray(expectedWrapper)
            val wrapper = ProtoBuf.decodeFromByteArray<ProtobufWrapper>(protobuf)

            // then
            assertEquals(expectedWrapper, wrapper)
        }

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    private data class ProtobufWrapper(
        @ProtoNumber(1)
        @Serializable(with = AttoTransactionAsByteArraySerializer::class)
        val transaction: AttoTransaction,
    )
}
