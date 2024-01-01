package cash.atto.commons

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

class AttoBlockTest {
    @ParameterizedTest
    @MethodSource("blockProvider")
    fun `should validate`(block: AttoBlock) {
        assertTrue(block.isValid())
    }

    @ParameterizedTest
    @MethodSource("invalidBlockProvider")
    fun `should not validate`(block: AttoBlock) {
        assertFalse(block.isValid())
    }

    @ParameterizedTest
    @MethodSource("blockProvider")
    fun `should serialize and deserialize json`(expectedBlock: AttoBlock) {
        // when
        val json = Json.encodeToString(AttoBlock.serializer(), expectedBlock)
        val block = Json.decodeFromString<AttoBlock>(json)

        // then
        assertEquals(expectedBlock, block)
    }

    @ParameterizedTest
    @MethodSource("blockProvider")
    fun `should serialize and deserialize bytes`(expectedBlock: AttoBlock) {
        // when
        val bytes = expectedBlock.toByteBuffer()
        val block = AttoBlock.fromByteBuffer(bytes)

        // then
        assertEquals(expectedBlock, block)
    }

    companion object {
        @JvmStatic
        fun blockProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(sendBlock),
                Arguments.of(receiveBlock),
                Arguments.of(openBlock),
                Arguments.of(changeBlock),
            )
        }

        @JvmStatic
        fun invalidBlockProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(sendBlock.copy(version = UShort.MAX_VALUE)), // unknown version
                Arguments.of(receiveBlock.copy(timestamp = Clock.System.now().plus(1.days))), // future timestamp
                Arguments.of(sendBlock.copy(height = 0U)), // invalid height
                Arguments.of(receiveBlock.copy(height = 0U)), // invalid height
                Arguments.of(changeBlock.copy(height = 0U)), // invalid height
                Arguments.of(sendBlock.copy(amount = AttoAmount.MIN)), // zero amount
                Arguments.of(sendBlock.copy(receiverPublicKey = sendBlock.publicKey)), // self send
                Arguments.of(receiveBlock.copy(balance = AttoAmount.MIN)), // zero balance
                Arguments.of(openBlock.copy(algorithm = AttoAlgorithm.UNKNOWN)), // unknown account algorithm
                Arguments.of(sendBlock.copy(algorithm = AttoAlgorithm.UNKNOWN)), // unknown account algorithm
                Arguments.of(receiveBlock.copy(algorithm = AttoAlgorithm.UNKNOWN)), // unknown account algorithm
                Arguments.of(changeBlock.copy(algorithm = AttoAlgorithm.UNKNOWN)), // unknown account algorithm
                Arguments.of(openBlock.copy(sendHashAlgorithm = AttoAlgorithm.UNKNOWN)), // unknown send algorithm
                Arguments.of(receiveBlock.copy(sendHashAlgorithm = AttoAlgorithm.UNKNOWN)), // unknown send algorithm
                Arguments.of(sendBlock.copy(receiverPublicKeyAlgorithm = AttoAlgorithm.UNKNOWN)), // unknown receiver algorithm
            )
        }
    }
}


val openBlock = AttoOpenBlock(
    version = 0U,
    algorithm = AttoAlgorithm.V1,
    publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    balance = AttoAmount.MAX,
    timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
    sendHashAlgorithm = AttoAlgorithm.V1,
    sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32))),
    representative = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
)

val sendBlock = AttoSendBlock(
    version = 0U,
    algorithm = AttoAlgorithm.V1,
    publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    height = 2U,
    balance = AttoAmount(1U),
    timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
    previous = AttoHash(Random.Default.nextBytes(ByteArray(32))),
    receiverPublicKeyAlgorithm = AttoAlgorithm.V1,
    receiverPublicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    amount = AttoAmount(1U),
)

val receiveBlock = AttoReceiveBlock(
    version = 0U,
    algorithm = AttoAlgorithm.V1,
    publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    height = 2U,
    balance = AttoAmount.MAX,
    timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
    previous = AttoHash(Random.nextBytes(ByteArray(32))),
    sendHashAlgorithm = AttoAlgorithm.V1,
    sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32)))
)

val changeBlock = AttoChangeBlock(
    version = 0U,
    algorithm = AttoAlgorithm.V1,
    publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    height = 2U,
    balance = AttoAmount.MAX,
    timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
    previous = AttoHash(Random.nextBytes(ByteArray(32))),
    representative = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
)