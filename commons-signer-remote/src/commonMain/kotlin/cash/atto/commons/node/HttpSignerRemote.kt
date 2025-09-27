package cash.atto.commons.node

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoChallenge
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoSigner
import cash.atto.commons.AttoVote
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val httpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }
        install(HttpTimeout)

        expectSuccess = true
    }

internal class HttpSignerRemote(
    private val url: String,
    private val retryEvery: Duration = 1.seconds,
    private val headerProvider: suspend () -> Map<String, String> = { emptyMap() },
) : AttoSigner {
    private val logger = KotlinLogging.logger {}

    override val publicKey: AttoPublicKey by lazy {
        runBlocking {
            getPublicKey()
        }
    }

    private suspend fun getPublicKey(): AttoPublicKey {
        while (currentCoroutineContext().isActive) {
            try {
                val headers = headerProvider.invoke()

                return httpClient
                    .get("$url/public-keys") {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        headers {
                            headers.forEach { (key, value) -> append(key, value) }
                        }
                        timeout {
                            socketTimeoutMillis = 5.seconds.inWholeMilliseconds
                        }
                    }.body<PublicKeyResponse>()
                    .publicKey
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get publicKey. Retrying in $retryEvery" }
                delay(retryEvery)
            }
        }
        throw CancellationException("Getting public key cancelled.")
    }

    override suspend fun sign(hash: AttoHash): AttoSignature = throw NotImplementedError("Remote signer doesn't accept direct hash signing")

    override suspend fun sign(
        challenge: AttoChallenge,
        timestamp: AttoInstant,
    ): AttoSignature {
        val uri = "$url/challenges"

        val request =
            ChallengeSignatureRequest(
                target = challenge,
                timestamp = timestamp,
            )

        while (currentCoroutineContext().isActive) {
            try {
                val headers = headerProvider.invoke()

                return httpClient
                    .post(uri) {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        setBody(request)
                        headers {
                            headers.forEach { (key, value) -> append(key, value) }
                        }
                        timeout {
                            socketTimeoutMillis = 1.seconds.inWholeMilliseconds
                        }
                    }.body<SignatureResponse>()
                    .signature
            } catch (e: Exception) {
                logger.warn(e) { "Failed to sign $challenge. Retrying in $retryEvery" }
                delay(retryEvery)
            }
        }
        throw CancellationException("Signing cancelled.")
    }

    override suspend fun sign(block: AttoBlock): AttoSignature {
        checkPublicKey(block.publicKey)

        val uri = "$url/blocks"

        val request =
            BlockSignatureRequest(
                target = block,
            )

        while (currentCoroutineContext().isActive) {
            try {
                val headers = headerProvider.invoke()

                return httpClient
                    .post(uri) {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        setBody(request)
                        headers {
                            headers.forEach { (key, value) -> append(key, value) }
                        }
                        timeout {
                            socketTimeoutMillis = 1.seconds.inWholeMilliseconds
                        }
                    }.body<SignatureResponse>()
                    .signature
            } catch (e: Exception) {
                logger.warn(e) { "Failed to sign $block. Retrying in $retryEvery" }
                delay(retryEvery)
            }
        }
        throw CancellationException("Signing cancelled.")
    }

    override suspend fun sign(vote: AttoVote): AttoSignature {
        checkPublicKey(vote.publicKey)

        val uri = "$url/votes"

        val request =
            VoteSignatureRequest(
                target = vote,
            )

        while (currentCoroutineContext().isActive) {
            try {
                val headers = headerProvider.invoke()

                return httpClient
                    .post(uri) {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        setBody(request)
                        headers {
                            headers.forEach { (key, value) -> append(key, value) }
                        }
                        timeout {
                            socketTimeoutMillis = 1.seconds.inWholeMilliseconds
                        }
                    }.body<SignatureResponse>()
                    .signature
            } catch (e: Exception) {
                if (e is ClientRequestException && e.response.status == HttpStatusCode.BadRequest) {
                    throw e
                }
                logger.warn(e) { "Failed to sign $vote. Retrying in $retryEvery" }
                delay(retryEvery)
            }
        }
        throw CancellationException("Signing cancelled.")
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
        val timestamp: AttoInstant,
    ) : SignatureRequest<AttoChallenge>

    @Serializable
    data class SignatureResponse(
        val signature: AttoSignature,
    )
}

fun AttoSigner.Companion.remote(
    url: String,
    retryEvery: Duration = 1.seconds,
    headerProvider: suspend () -> Map<String, String>,
): AttoSigner = HttpSignerRemote(url, retryEvery, headerProvider)
