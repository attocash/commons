package cash.atto.commons

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

enum class AttoNetwork(
    val code: UByte,
    val thresholdIncreaseFactor: ULong,
) {
    LIVE(0U, 1u),
    BETA(1U, 10u),
    DEV(2U, 100u),
    LOCAL(3U, 100_000u),

    UNKNOWN(9U, ULong.MAX_VALUE),
    ;

    companion object {
        val INITIAL_LIVE_THRESHOLD = 8589934591UL
        val INITIAL_DATE: LocalDate = LocalDate(2024, 1, 1)
        val INITIAL_INSTANT: Instant = INITIAL_DATE.atStartOfDayIn(TimeZone.UTC)
        val DOUBLING_PERIOD = 2.0

        private val map = entries.associateBy { it.code }

        fun from(code: UByte): AttoNetwork = map[code] ?: UNKNOWN
    }
}
