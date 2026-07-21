package cash.atto.commons.node

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoChallenge
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoSigner
import cash.atto.commons.AttoVote
import cash.atto.commons.transport.AttoHttpTimeouts
import cash.atto.commons.transport.AttoHttpTransport
import cash.atto.commons.transport.httpStatusCodeOrNull
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class HttpSignerRemote(
    url: String,
    private val retryEvery: Duration = 1.seconds,
    headerProvider: suspend () -> Map<String, String> = { emptyMap() },
    private val maxAttempts: UInt? = null,
) : AttoSigner {
    private val logger = KotlinLogging.logger {}
    private val transport = AttoHttpTransport(url, headerProvider)

    override val algorithm: AttoAlgorithm = AttoAlgorithm.V1
    override val publicKey: AttoPublicKey by lazy {
        runBlocking {
            getPublicKey()
        }
    }
    override val address: AttoAddress by lazy { AttoAddress(algorithm, publicKey) }

    private suspend fun <T> retrying(
        operation: String,
        action: suspend () -> T,
    ): T {
        var attempts = 0U
        while (currentCoroutineContext().isActive) {
            try {
                return action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.cancellationCause()?.let { throw it }

                val terminalStatus = e.terminalClientStatus()
                if (terminalStatus != null) {
                    throw AttoRemoteSignerTerminalException(operation, terminalStatus, e)
                }

                attempts += 1U
                if (maxAttempts != null && attempts >= maxAttempts) {
                    throw AttoRemoteSignerRetriesExhaustedException(operation, attempts, e)
                }

                logger.warn(e) { "Failed to $operation. Retrying in $retryEvery" }
                delay(retryEvery)
            }
        }
        throw CancellationException("$operation cancelled.")
    }

    private fun Throwable.cancellationCause(): CancellationException? {
        var cause = cause
        while (cause != null) {
            if (cause is CancellationException) {
                return cause
            }
            cause = cause.cause
        }
        return null
    }

    private fun Exception.terminalClientStatus(): HttpStatusCode? {
        val status = httpStatusCodeOrNull()?.let(HttpStatusCode::fromValue) ?: return null
        if (status == HttpStatusCode.RequestTimeout || status == HttpStatusCode.TooManyRequests) return null
        return status
    }

    private suspend fun getPublicKey(): AttoPublicKey =
        retrying("get publicKey") {
            transport
                .get<PublicKeyResponse>(
                    path = "public-keys",
                    timeouts = AttoHttpTimeouts(socket = 5.seconds),
                ).publicKey
        }

    override suspend fun sign(hash: AttoHash): AttoSignature = throw NotImplementedError("Remote signer doesn't accept direct hash signing")

    override suspend fun sign(
        challenge: AttoChallenge,
        timestamp: AttoInstant,
    ): AttoSignature {
        val request =
            ChallengeSignatureRequest(
                target = challenge,
                timestamp = timestamp,
            )

        val signature =
            retrying("sign $challenge") {
                transport
                    .post<ChallengeSignatureRequest, SignatureResponse>(
                        path = "challenges",
                        body = request,
                        timeouts = AttoHttpTimeouts(socket = 1.seconds),
                    ).signature
            }

        if (!signature.isValid(publicKey, challenge, timestamp)) {
            throw AttoRemoteSignerInvalidSignatureException("challenge")
        }
        return signature
    }

    override suspend fun sign(block: AttoBlock): AttoSignature {
        checkPublicKey(block.publicKey)

        val request =
            BlockSignatureRequest(
                target = block,
            )

        val signature =
            retrying("sign $block") {
                transport
                    .post<BlockSignatureRequest, SignatureResponse>(
                        path = "blocks",
                        body = request,
                        timeouts = AttoHttpTimeouts(socket = 1.seconds),
                    ).signature
            }

        if (!signature.isValid(block.publicKey, block.hash)) {
            throw AttoRemoteSignerInvalidSignatureException("block")
        }
        return signature
    }

    override suspend fun sign(vote: AttoVote): AttoSignature {
        checkPublicKey(vote.publicKey)

        val request =
            VoteSignatureRequest(
                target = vote,
            )

        val signature =
            retrying("sign $vote") {
                transport
                    .post<VoteSignatureRequest, SignatureResponse>(
                        path = "votes",
                        body = request,
                        timeouts = AttoHttpTimeouts(socket = 1.seconds),
                    ).signature
            }

        if (!signature.isValid(vote.publicKey, vote.hash)) {
            throw AttoRemoteSignerInvalidSignatureException("vote")
        }
        return signature
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

class AttoRemoteSignerTerminalException internal constructor(
    operation: String,
    val status: HttpStatusCode,
    cause: Throwable,
) : IllegalStateException("Remote signer failed to $operation with terminal status $status", cause)

class AttoRemoteSignerRetriesExhaustedException internal constructor(
    operation: String,
    attempts: UInt,
    cause: Throwable,
) : IllegalStateException("Remote signer failed to $operation after $attempts attempts", cause)

class AttoRemoteSignerInvalidSignatureException internal constructor(
    targetType: String,
) : IllegalStateException("Remote signer returned invalid $targetType signature")

fun AttoSigner.Companion.remote(
    url: String,
    retryEvery: Duration = 1.seconds,
    maxAttempts: UInt? = null,
    headerProvider: suspend () -> Map<String, String>,
): AttoSigner = HttpSignerRemote(url, retryEvery, headerProvider, maxAttempts)
