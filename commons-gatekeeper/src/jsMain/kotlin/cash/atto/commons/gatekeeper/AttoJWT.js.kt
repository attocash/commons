package cash.atto.commons.gatekeeper

@JsModule("jsonwebtoken")
@JsNonModule
external object JsonWebToken {
    fun decode(token: String): dynamic
}

actual fun AttoJWT.Companion.decode(encoded: String): AttoJWT {
    val decoded = JsonWebToken.decode(encoded)

    val expiresAtMillis = decoded.exp.unsafeCast<Int>().let { it * 1000L }

    return AttoJWT(
        expiresAt = expiresAtMillis.let { kotlinx.datetime.Instant.fromEpochMilliseconds(it) },
        encoded = encoded,
    )
}
