package cash.atto.commons

import java.time.*

enum class AttoNetwork(val environment: String, val thresholdIncreaseFactor: ULong) {
    LIVE("XT0", 1u),
    BETA("XT1", 10u),
    DEV("XT2", 100u),
    LOCAL("XT3", 1_000u),

    UNKNOWN("???", ULong.MAX_VALUE);

    companion object {
        val INITIAL_LIVE_THRESHOLD = (0x7FFFFFFFF).toULong()
        val INITIAL_DATE: LocalDate = LocalDate.of(2023, 1, 1)
        val INITIAL_INSTANT: Instant = OffsetDateTime.of(INITIAL_DATE, LocalTime.MIN, ZoneOffset.UTC).toInstant()
        val DOUBLING_PERIOD = 2.0

        private val map = entries.associateBy { it.environment }
        fun from(environment: String): AttoNetwork {
            return map.getOrDefault(environment, UNKNOWN)
        }
    }
}