package cash.atto.commons.gatekeeper

import com.auth0.jwt.JWT
import kotlinx.datetime.toKotlinInstant

actual fun AttoJWT.Companion.decode(encoded: String): AttoJWT {
    val jwt = JWT.decode(encoded)
    return AttoJWT(
        expiresAt = jwt.expiresAtAsInstant.toKotlinInstant(),
        encoded = encoded,
    )
}
