package cash.atto.commons

import java.time.Instant

fun Instant.toAtto(): AttoInstant = AttoInstant.fromEpochMilliseconds(this.toEpochMilli())
