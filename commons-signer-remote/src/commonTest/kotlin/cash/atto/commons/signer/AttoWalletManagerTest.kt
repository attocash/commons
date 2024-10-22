package cash.atto.commons.signer

import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoChallenge
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoReceiveBlock
import cash.atto.commons.AttoSigner
import cash.atto.commons.AttoVote
import cash.atto.commons.generate
import cash.atto.commons.isValid
import cash.atto.commons.toAttoAmount
import cash.atto.commons.toAttoHeight
import cash.atto.commons.toAttoVersion
import cash.atto.commons.toPublicKey
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

expect fun randomPort(): Int

class AttoWalletManagerTest {
    companion object {
        private val port = randomPort()
        private val backend = AttoTestRemoteSigner(port)
        private val signer = AttoSigner.remote("http://localhost:$port")

        init {
            backend.start()
        }
    }

    @Test
    fun `should sign block`(): Unit = runBlocking {
        // given
        val block = AttoBlock.sample()

        // when
        val signature = signer.sign(block)

        // then
        assertTrue { signature.isValid(signer.publicKey, block.hash) }
    }

    @Test
    fun `should sign vote`(): Unit = runBlocking {
        // given
        val block = AttoVote.sample()

        // when
        val signature = signer.sign(block)

        // then
        assertTrue { signature.isValid(signer.publicKey, block.hash) }
    }

    @Test
    fun `should sign challenge`(): Unit = runBlocking {
        // given
        val challenge = AttoChallenge.generate()

        // when
        val signature = signer.sign(challenge)

        // then
        val hash = AttoHash.hash(64, signer.publicKey.value, challenge.value)
        assertTrue { signature.isValid(signer.publicKey, hash) }
    }


    private fun AttoVote.Companion.sample(): AttoVote {
        return AttoVote(
            AttoAlgorithm.V1,
            blockHash = AttoHash(Random.nextBytes(ByteArray(32))),
            timestamp = Clock.System.now(),
        )
    }

    private fun AttoBlock.Companion.sample(): AttoBlock {
        return AttoReceiveBlock(
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
    }

    private fun AttoReceivable.Companion.sample(): AttoReceivable {
        return AttoReceivable(
            version = 0U.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            receiverAlgorithm = AttoAlgorithm.V1,
            receiverPublicKey = signer.publicKey,
            amount = 1000UL.toAttoAmount(),
            timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
            hash = AttoHash(Random.Default.nextBytes(32))
        )
    }
}
