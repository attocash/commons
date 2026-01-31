package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
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
        @JsExport.Ignore
        val INITIAL_LIVE_THRESHOLD = 8589934591UL

        @JsExport.Ignore
        val INITIAL_DATE = LocalDate(2024, 1, 1)

        @JsExport.Ignore
        @OptIn(ExperimentalTime::class)
        val INITIAL_INSTANT = AttoInstant.fromEpochMilliseconds(INITIAL_DATE.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())

        @JsExport.Ignore
        val DOUBLING_PERIOD = 2.0

        private val map = entries.associateBy { it.code }

        @JsExport.Ignore
        fun from(code: UByte): AttoNetwork = map[code] ?: UNKNOWN
    }
}
