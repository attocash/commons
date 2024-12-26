package cash.atto.commons.signer

import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoChallenge
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoReceiveBlock
import cash.atto.commons.AttoSigner
import cash.atto.commons.AttoVersion
import cash.atto.commons.AttoVote
import cash.atto.commons.generate
import cash.atto.commons.isValid
import cash.atto.commons.toAttoAmount
import cash.atto.commons.toAttoHeight
import cash.atto.commons.toAttoVersion
import cash.atto.commons.toByteArray
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

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
            val timestamp = Clock.System.now()

            // when
            val signature = signer.sign(challenge, timestamp)

            // then
            val hash = AttoHash.hash(64, signer.publicKey.value, challenge.value, timestamp.toByteArray())
            assertTrue { signature.isValid(signer.publicKey, hash) }
        }

    private fun AttoVote.Companion.sample(): AttoVote =
        AttoVote(
            version = AttoVersion(0U),
            algorithm = AttoAlgorithm.V1,
            publicKey = signer.publicKey,
            blockAlgorithm = AttoAlgorithm.V1,
            blockHash = AttoHash(Random.nextBytes(ByteArray(32))),
            timestamp = Clock.System.now(),
        )

    private fun AttoBlock.Companion.sample(): AttoBlock =
        AttoReceiveBlock(
            version = 0U.toAttoVersion(),
            network = AttoNetwork.LOCAL,
            algorithm = AttoAlgorithm.V1,
            publicKey = signer.publicKey,
            height = 2U.toAttoHeight(),
            balance = AttoAmount.MAX,
            timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
            previous = AttoHash(Random.nextBytes(ByteArray(32))),
            sendHashAlgorithm = AttoAlgorithm.V1,
            sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32))),
        )

    private fun AttoReceivable.Companion.sample(): AttoReceivable =
        AttoReceivable(
            hash = AttoHash(Random.Default.nextBytes(32)),
            version = 0U.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            publicKey = AttoPublicKey(Random.nextBytes(32)),
            timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
            receiverAlgorithm = AttoAlgorithm.V1,
            receiverPublicKey = signer.publicKey,
            amount = 1000UL.toAttoAmount(),
        )
}
