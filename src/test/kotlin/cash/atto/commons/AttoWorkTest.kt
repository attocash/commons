package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import cash.atto.commons.AttoNetwork.Companion.INITIAL_LIVE_THRESHOLD
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.stream.Stream

internal class AttoWorkTest {
    private val hash = AttoHash("0000000000000000000000000000000000000000000000000000000000000000".fromHexToByteArray())

    @Test
    fun `should perform work`() {
        val network = AttoNetwork.LOCAL
        val timestamp = AttoNetwork.INITIAL_INSTANT
        val work = AttoWork.work(network, timestamp, hash)
        assertTrue(AttoWork.isValid(network, timestamp, hash, work))
    }

    @Test
    fun `should validate work`() {
        val work = AttoWork("a7e077e02e3e759f".fromHexToByteArray())
        assertTrue(work.isValid(AttoNetwork.LIVE, AttoNetwork.INITIAL_INSTANT, hash))
    }

    @Test
    fun `should validate work using decreased threshold`() {
        val work = AttoWork("888f6824075cabc3".fromHexToByteArray())
        val timestamp =
            OffsetDateTime.of(AttoNetwork.INITIAL_DATE.plusYears(4), LocalTime.MIN, ZoneOffset.UTC).toInstant()
        assertTrue(work.isValid(AttoNetwork.LIVE, timestamp, hash))
    }

    @Test
    fun `should not validate when work is below threshold`() {
        val work = AttoWork("a7e077e02e3e759f".fromHexToByteArray())
        val timestamp =
            OffsetDateTime.of(AttoNetwork.INITIAL_DATE.plusYears(4), LocalTime.MIN, ZoneOffset.UTC).toInstant()
        assertFalse(work.isValid(AttoNetwork.LIVE, timestamp, hash))
    }

    @ParameterizedTest
    @MethodSource("provider")
    fun `should return threshold`(instant: Instant, expectedThreshold: Long) {
        val threshold = AttoWork.threshold(AttoNetwork.LIVE, instant)
        assertEquals(expectedThreshold.toULong(), threshold)
    }

    companion object {
        @JvmStatic
        fun provider(): Stream<Arguments> {
            val calculateTimestamp = fun(years: Long): Instant {
                return INITIAL_INSTANT.atZone(ZoneOffset.UTC).plusYears(years).toInstant()
            }
            val calculateThreshold = fun(decreaseFactor: UInt): Long {
                return (INITIAL_LIVE_THRESHOLD / decreaseFactor).toLong()
            }
            return Stream.of(
                Arguments.of(calculateTimestamp.invoke(0), calculateThreshold(1U)),
                Arguments.of(calculateTimestamp.invoke(1), calculateThreshold(1U)),
                Arguments.of(calculateTimestamp.invoke(2), calculateThreshold(2U)),
                Arguments.of(calculateTimestamp.invoke(3), calculateThreshold(2U)),
                Arguments.of(calculateTimestamp.invoke(4), calculateThreshold(4U)),
                Arguments.of(calculateTimestamp.invoke(5), calculateThreshold(5U)),
                Arguments.of(calculateTimestamp.invoke(6), calculateThreshold(8U)),
            )
        }
    }
}