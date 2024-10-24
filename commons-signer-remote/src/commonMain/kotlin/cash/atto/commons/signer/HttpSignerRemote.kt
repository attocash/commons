package cash.atto.commons.signer

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoChallenge
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoSigner
import cash.atto.commons.AttoVote
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

private val httpClient = HttpClient {
    install(ContentNegotiation) {
        json()
    }
    install(HttpTimeout)
}


internal class HttpSignerRemote(
    private val url: String,
    private val headerProvider: suspend () -> Map<String, String> = { emptyMap() }
) : AttoSigner {
    override val publicKey: AttoPublicKey by lazy {
        runBlocking {
            val headers = headerProvider.invoke()

            httpClient.get("$url/public-keys") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                headers {
                    headers.forEach { (key, value) -> append(key, value) }
                }
                timeout {
                    socketTimeoutMillis = 5.seconds.inWholeMilliseconds
                }
            }
                .body<PublicKeyResponse>()
                .publicKey
        }
    }

    override suspend fun sign(hash: AttoHash): AttoSignature {
        throw NotImplementedError("Remote signer doesn't accept direct hash signing")
    }

    override suspend fun sign(challenge: AttoChallenge, timestamp: Instant): AttoSignature {
        val uri = "$url/challenges"

        val request = ChallengeSignatureRequest(
            target = challenge,
            timestamp = timestamp
        )

        val headers = headerProvider.invoke()

        return httpClient.post(uri) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(request)
            headers {
                headers.forEach { (key, value) -> append(key, value) }
            }
            timeout {
                socketTimeoutMillis = 1.seconds.inWholeMilliseconds
            }
        }
            .body<SignatureResponse>()
            .signature
    }

    override suspend fun sign(block: AttoBlock): AttoSignature {
        val uri = "$url/blocks"

        val request = BlockSignatureRequest(
            target = block
        )

        val headers = headerProvider.invoke()

        return httpClient.post(uri) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(request)
            headers {
                headers.forEach { (key, value) -> append(key, value) }
            }
            timeout {
                socketTimeoutMillis = 1.seconds.inWholeMilliseconds
            }
        }
            .body<SignatureResponse>()
            .signature
    }

    override suspend fun sign(vote: AttoVote): AttoSignature {
        val uri = "$url/votes"

        val request = VoteSignatureRequest(
            target = vote
        )

        val headers = headerProvider.invoke()

        return httpClient.post(uri) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(request)
            headers {
                headers.forEach { (key, value) -> append(key, value) }
            }
            timeout {
                socketTimeoutMillis = 1.seconds.inWholeMilliseconds
            }
        }
            .body<SignatureResponse>()
            .signature
    }


    @Serializable
    data class PublicKeyResponse(
        val publicKey: AttoPublicKey,
    )

    interface SignatureRequest<T> {
        val target: T
    }

    @Serializable
    data class BlockSignatureRequest(
        override val target: AttoBlock,
    ) : SignatureRequest<AttoBlock>

    @Serializable
    data class VoteSignatureRequest(
        override val target: AttoVote,
    ) : SignatureRequest<AttoVote>

    @Serializable
    data class ChallengeSignatureRequest(
        override val target: AttoChallenge,
        val timestamp: Instant,
    ) : SignatureRequest<AttoChallenge>

    @Serializable
    data class SignatureResponse(
        val signature: AttoSignature,
    )
}

fun AttoSigner.Companion.remote(
    url: String,
    headerProvider: suspend () -> Map<String, String>
): AttoSigner {
    return HttpSignerRemote(url, headerProvider)
}