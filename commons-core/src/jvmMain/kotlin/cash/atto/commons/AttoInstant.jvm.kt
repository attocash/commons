package cash.atto.commons

import java.time.Instant
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

fun Instant.toAtto(): AttoInstant = AttoInstant.fromEpochMilliseconds(this.toEpochMilli())

@OptIn(ExperimentalTime::class)
fun AttoInstant.toJavaInstant(): Instant = this.value.toJavaInstant()
