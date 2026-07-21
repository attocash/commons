package cash.atto.commons

import cash.atto.commons.worker.AttoWorker
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttoTransactionTest {
    private val privateKey = AttoPrivateKey.generate()

    @Test
    fun `should validate`() =
        runTest {
            // given
            val (signer, receiveBlock) = fixture()
            val transaction =
                AttoTransaction(
                    block = receiveBlock,
                    signature = signer.sign(receiveBlock),
                    work = AttoWorker.cpu().work(receiveBlock),
                )

            assertTrue { transaction.isValid() }
        }

    @Test
    fun `should return error when block is invalid`() =
        runTest {
            // given
            val (signer, receiveBlock) = fixture()
            val block = receiveBlock.copy(version = UShort.MAX_VALUE.toAttoVersion())
            val transaction =
                AttoTransaction(
                    block = block,
                    signature = signer.sign(block),
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
            val (signer, receiveBlock) = fixture()
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
                    signature = signer.sign(receiveBlock),
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
            val (_, receiveBlock) = fixture()
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

            val decoded = AttoTransaction.fromByteArray(transaction.toByteArray())
            assertEquals(transaction, decoded)
            assertFalse { decoded.isValid() }
        }

    @Test
    fun `should serialize buffer`() =
        runTest {
            // given
            val (signer, receiveBlock) = fixture()
            val expectedTransaction =
                AttoTransaction(
                    block = receiveBlock,
                    signature = signer.sign(receiveBlock),
                    work = AttoWorker.cpu().work(receiveBlock),
                )

            // when
            val buffer = expectedTransaction.toBuffer()
            val transaction = AttoTransaction.fromBuffer(buffer)

            // then
            assertEquals(expectedTransaction, transaction)
        }

    @Test
    fun `should serialize and deserialize byte array`() =
        runTest {
            // given
            val (signer, receiveBlock) = fixture()
            val expectedTransaction =
                AttoTransaction(
                    block = receiveBlock,
                    signature = signer.sign(receiveBlock),
                    work = AttoWorker.cpu().work(receiveBlock),
                )

            // when
            val bytes = expectedTransaction.toByteArray()
            val transaction = AttoTransaction.fromByteArray(bytes)

            // then
            assertEquals(expectedTransaction, transaction)
        }

    @Test
    fun `should reject trailing bytes`() =
        runTest {
            val (signer, receiveBlock) = fixture()
            val transaction =
                AttoTransaction(
                    block = receiveBlock,
                    signature = signer.sign(receiveBlock),
                    work = AttoWorker.cpu().work(receiveBlock),
                )
            val buffer = transaction.toBuffer()
            buffer.write(byteArrayOf(0))

            assertFailsWith<IllegalArgumentException> {
                AttoTransaction.fromBuffer(buffer)
            }
        }

    @Test
    fun `should serialize and deserialize json`() =
        runTest {
            // given
            val (signer, receiveBlock) = fixture()
            val expectedTransaction =
                AttoTransaction(
                    block = receiveBlock,
                    signature = signer.sign(receiveBlock),
                    work = AttoWorker.cpu().work(receiveBlock),
                )

            // when
            val json = expectedTransaction.toJson()
            val transaction = AttoTransaction.fromJson(json)

            // then
            assertEquals(expectedTransaction, transaction)
        }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `should serialize protobuf`() =
        runTest {
            // given
            val (signer, receiveBlock) = fixture()
            val expectedTransaction =
                AttoTransaction(
                    block = receiveBlock,
                    signature = signer.sign(receiveBlock),
                    work = AttoWorker.cpu().work(receiveBlock),
                )
            val expectedWrapper = ProtobufWrapper(expectedTransaction)

            // when
            val protobuf = ProtoBuf.encodeToByteArray(expectedWrapper)
            val wrapper = ProtoBuf.decodeFromByteArray<ProtobufWrapper>(protobuf)

            // then
            assertEquals(expectedWrapper, wrapper)
        }

    private suspend fun fixture(): Pair<AttoSigner, AttoReceiveBlock> {
        val publicKey = privateKey.toPublicKey()
        val signer = privateKey.toSigner()
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
        return signer to receiveBlock
    }

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    private data class ProtobufWrapper(
        @ProtoNumber(1)
        @Serializable(with = AttoTransactionAsByteArraySerializer::class)
        val transaction: AttoTransaction,
    )
}
