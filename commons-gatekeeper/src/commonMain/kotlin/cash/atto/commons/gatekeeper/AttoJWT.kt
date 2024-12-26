package cash.atto.commons.gatekeeper

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

data class AttoJWT(
    val expiresAt: Instant,
    val encoded: String,
) {
    companion object {}

    fun isExpired(leeway: Duration): Boolean = expiresAt < Clock.System.now().minus(leeway)
}

expect fun AttoJWT.Companion.decode(encoded: String): AttoJWT
