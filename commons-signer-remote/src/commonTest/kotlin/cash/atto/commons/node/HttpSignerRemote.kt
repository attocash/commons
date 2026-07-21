package cash.atto.commons.node

import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoChallenge
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoReceiveBlock
import cash.atto.commons.AttoSigner
import cash.atto.commons.AttoVersion
import cash.atto.commons.AttoVote
import cash.atto.commons.toAttoAmount
import cash.atto.commons.toAttoHeight
import cash.atto.commons.toAttoVersion
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

expect fun randomPort(): Int

class SignerRemoteTest {
    companion object {
        private val port = randomPort()
        private val backend = MocktRemoteSigner(port)
        private val signer =
            AttoSigner.remote("http://localhost:$port") {
                mapOf()
            }

        init {
            backend.start()
        }
    }

    @Test
    fun `should sign block`(): Unit =
        runBlocking {
            // given
            val block = AttoBlock.sample()

            // when
            val signature = signer.sign(block)

            // then
            assertTrue { signature.isValid(signer.publicKey, block.hash) }
        }

    @Test
    fun `should sign vote`(): Unit =
        runBlocking {
            // given
            val block = AttoVote.sample()

            // when
            val signature = signer.sign(block)

            // then
            assertTrue { signature.isValid(signer.publicKey, block.hash) }
        }

    @Test
    fun `should sign challenge`(): Unit =
        runBlocking {
            // given
            val challenge = AttoChallenge.generate()
            val timestamp = AttoInstant.now()

            // when
            val signature = signer.sign(challenge, timestamp)

            // then
            assertTrue { signature.isValid(signer.publicKey, challenge, timestamp) }
        }

    @Test
    fun `should fail fast on terminal client error`(): Unit =
        runBlocking {
            val port = randomPort()
            val backend = MocktRemoteSigner(port, statusByPath = mapOf("/blocks" to HttpStatusCode.Unauthorized))
            backend.start()
            try {
                val signer =
                    AttoSigner.remote(
                        "http://localhost:$port",
                        retryEvery = 10.seconds,
                        headerProvider = { emptyMap() },
                        maxAttempts = 1U,
                    )
                val block = AttoBlock.sample(signer.publicKey)

                assertFailsWith<AttoRemoteSignerTerminalException> {
                    signer.sign(block)
                }
            } finally {
                backend.stop()
            }
        }

    @Test
    fun `should reject invalid remote signature`(): Unit =
        runBlocking {
            val port = randomPort()
            val backend = MocktRemoteSigner(port, invalidSignatures = true)
            backend.start()
            try {
                val signer =
                    AttoSigner.remote(
                        "http://localhost:$port",
                        retryEvery = 10.seconds,
                        headerProvider = { emptyMap() },
                        maxAttempts = 1U,
                    )
                val block = AttoBlock.sample(signer.publicKey)

                assertFailsWith<AttoRemoteSignerInvalidSignatureException> {
                    signer.sign(block)
                }
            } finally {
                backend.stop()
            }
        }

    @Test
    fun `should not retry cancelled vote signing`(): Unit =
        runBlocking {
            val port = randomPort()
            val backend = MocktRemoteSigner(port, voteDelay = 10.seconds)
            backend.start()
            try {
                val signer =
                    AttoSigner.remote(
                        "http://localhost:$port",
                        retryEvery = 100.milliseconds,
                        headerProvider = { emptyMap() },
                    )
                val vote = AttoVote.sample(signer.publicKey)

                val signing = async { signer.sign(vote) }
                backend.voteRequestStarted.await()
                signing.cancelAndJoin()
                delay(250.milliseconds)

                assertEquals(1, backend.voteRequestCount)
            } finally {
                backend.stop()
            }
        }

    private fun AttoVote.Companion.sample(publicKey: AttoPublicKey = signer.publicKey): AttoVote =
        AttoVote(
            version = AttoVersion(0U),
            algorithm = AttoAlgorithm.V1,
            publicKey = publicKey,
            blockAlgorithm = AttoAlgorithm.V1,
            blockHash = AttoHash(Random.nextBytes(ByteArray(32))),
            timestamp = AttoInstant.now(),
        )

    private fun AttoBlock.Companion.sample(publicKey: AttoPublicKey = signer.publicKey): AttoBlock =
        AttoReceiveBlock(
            version = 0U.toAttoVersion(),
            network = AttoNetwork.LOCAL,
            algorithm = AttoAlgorithm.V1,
            publicKey = publicKey,
            height = 2U.toAttoHeight(),
            balance = AttoAmount.MAX,
            timestamp = AttoInstant.now(),
            previous = AttoHash(Random.nextBytes(ByteArray(32))),
            sendHashAlgorithm = AttoAlgorithm.V1,
            sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32))),
        )

    private fun AttoReceivable.Companion.sample(): AttoReceivable =
        AttoReceivable(
            network = AttoNetwork.LOCAL,
            hash = AttoHash(Random.Default.nextBytes(32)),
            version = 0U.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            publicKey = AttoPublicKey(Random.nextBytes(32)),
            timestamp = AttoInstant.now(),
            receiverAlgorithm = AttoAlgorithm.V1,
            receiverPublicKey = signer.publicKey,
            amount = 1000UL.toAttoAmount(),
        )
}
