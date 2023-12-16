package cash.atto.commons

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
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
            )
        }
    }
}