package cash.atto.commons.gatekeeper

import cash.atto.commons.AttoInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration

data class AttoJWT(
    val expiresAt: AttoInstant,
    val encoded: String,
) {
    companion object {}

    fun isExpired(leeway: Duration): Boolean = expiresAt < AttoInstant.now().minus(leeway)
}

@Serializable
internal data class JwtPayload(
    val exp: Long? = null,
)

private val json = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalEncodingApi::class)
fun AttoJWT.Companion.decode(encoded: String): AttoJWT {
    val parts = encoded.split('.')
    require(parts.size >= 2) { "Invalid JWT format: not enough parts" }

    val base64UrlPayload = parts[1]

    val base64Payload =
        base64UrlPayload
            .replace('-', '+')
            .replace('_', '/')
            .let { it.padEnd((it.length + 3) / 4 * 4, '=') }

    val payloadBytes = Base64.decode(base64Payload)

    val payloadJson = payloadBytes.decodeToString()

    val payload = json.decodeFromString<JwtPayload>(payloadJson)

    val expMillis =
        payload.exp?.times(1000)
            ?: throw IllegalArgumentException("No exp field found in JWT payload")

    return AttoJWT(
        expiresAt = AttoInstant.fromEpochMilliseconds(expMillis),
        encoded = encoded,
    )
}
