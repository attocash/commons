package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import cash.atto.commons.AttoNetwork.Companion.INITIAL_LIVE_THRESHOLD
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

internal class AttoWorkTest {
    @ParameterizedTest
    @MethodSource("provider")
    fun `should return threshold`(
        instant: Instant,
        expectedThreshold: Long,
    ) {
        val threshold = AttoWork.threshold(AttoNetwork.LIVE, instant)
        assertEquals(expectedThreshold.toULong(), threshold)
    }

    companion object {
        @JvmStatic
        fun provider(): Stream<Arguments> {
            val calculateTimestamp = fun(years: Long): Instant {
                return INITIAL_INSTANT.plus(years, DateTimeUnit.YEAR, TimeZone.UTC)
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

    @Test
    fun `should serialize json`() {
        // given
        val expectedJson = "\"883175A7421F3696\""

        // when
        val work = Json.decodeFromString(AttoWorkSerializer, expectedJson)
        val json = Json.encodeToString(AttoWorkSerializer, work)

        // then
        assertEquals(expectedJson, json)
    }
}
