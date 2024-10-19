package cash.atto.commons.wallet

import cash.atto.commons.gatekeeper.AttoJWT
import cash.atto.commons.toHex
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.datetime.toKotlinInstant
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.time.Instant
import kotlin.time.Duration.Companion.days


private val jwtAlgorithm by lazy {
    val keyPairGenerator = KeyPairGenerator.getInstance("EC")
    keyPairGenerator.initialize(521)
    val keyPair = keyPairGenerator.generateKeyPair()
    val privateKey = keyPair.private as ECPrivateKey
    Algorithm.ECDSA512(null, privateKey)
}

actual fun generateJwt(): AttoJWT {
    val jwt = JWT
        .create()
        .withIssuer("test")
        .withAudience("http://localhost")
        .withIssuedAt(Instant.now())
        .withExpiresAt(Instant.now().plusSeconds(1.days.inWholeSeconds))
        .withSubject(ByteArray(32).toHex())
        .sign(jwtAlgorithm)

    val decodedJWT = JWT.decode(jwt)

    return AttoJWT(decodedJWT.expiresAtAsInstant.toKotlinInstant(), jwt)
}
