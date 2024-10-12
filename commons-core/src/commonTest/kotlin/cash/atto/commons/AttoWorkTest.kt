package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import cash.atto.commons.AttoNetwork.Companion.INITIAL_LIVE_THRESHOLD
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class AttoWorkTest {
    private val hash = AttoHash("0000000000000000000000000000000000000000000000000000000000000000".fromHexToByteArray())

    @Test
    fun `should return threshold`() {
        thresholdMap.forEach { (timestamp, expectedThreshold) ->
            val threshold = AttoWork.threshold(AttoNetwork.LIVE, timestamp)
            assertEquals(expectedThreshold.toULong(), threshold)
        }
    }

    companion object {
        val thresholdMap = HashMap<Instant, Long>().apply {
            val calculateTimestamp = fun(years: Long): Instant {
                return INITIAL_INSTANT.plus(years, DateTimeUnit.YEAR, TimeZone.UTC)
            }
            val calculateThreshold = fun(decreaseFactor: UInt): Long {
                return (INITIAL_LIVE_THRESHOLD / decreaseFactor).toLong()
            }

            put(calculateTimestamp.invoke(0), calculateThreshold(1U))
            put(calculateTimestamp.invoke(1), calculateThreshold(1U))
            put(calculateTimestamp.invoke(2), calculateThreshold(2U))
            put(calculateTimestamp.invoke(3), calculateThreshold(2U))
            put(calculateTimestamp.invoke(4), calculateThreshold(4U))
            put(calculateTimestamp.invoke(5), calculateThreshold(5U))
            put(calculateTimestamp.invoke(6), calculateThreshold(8U))
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

    @Test
    fun `should validate work`() {
        val work = AttoWork("576887B000000000".fromHexToByteArray())
        assertTrue(AttoWork.isValid(AttoNetwork.LIVE, AttoNetwork.INITIAL_INSTANT, hash.value, work.value))
    }

    @Test
    fun `should validate work using decreased threshold`() {
        val work = AttoWork("0582489800000000".fromHexToByteArray())
        val timestamp =
            AttoNetwork
                .INITIAL_DATE
                .plus(4, DateTimeUnit.YEAR)
                .atTime(LocalTime.fromSecondOfDay(0))
                .toInstant(TimeZone.UTC)
        assertTrue(AttoWork.isValid(AttoNetwork.LIVE, timestamp, hash.value, work.value))
    }

    @Test
    fun `should not validate when work is below threshold`() {
        val work = AttoWork("a7e077e02e3e759f".fromHexToByteArray())
        val timestamp =
            AttoNetwork
                .INITIAL_DATE
                .plus(4, DateTimeUnit.YEAR)
                .atTime(LocalTime.fromSecondOfDay(0))
                .toInstant(TimeZone.UTC)
        assertFalse(AttoWork.isValid(AttoNetwork.LIVE, timestamp, hash.value, work.value))
    }

    @Test
    fun `should not validate when timestamp is before initial date`() {
        val work = AttoWork("a7e077e02e3e759f".fromHexToByteArray())
        val timestamp = AttoNetwork.INITIAL_INSTANT.minus(1, DateTimeUnit.SECOND)
        assertFalse(AttoWork.isValid(AttoNetwork.LIVE, timestamp, hash.value, work.value))
    }
}
