package cash.atto.commons

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AttoVoteTest {
    private val privateKey = AttoPrivateKey.generate()
    private val publicKey = privateKey.toPublicKey()
    private val signer = privateKey.toSigner()

    private val vote =
        AttoVote(
            version = 0U.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            publicKey = publicKey,
            blockAlgorithm = AttoAlgorithm.V1,
            blockHash = AttoHash(Random.nextBytes(ByteArray(32))),
            timestamp = AttoInstant.now(),
        )

    @Test
    fun `should serialize and deserialize signed vote bytes`() =
        runTest {
            val signedVote = AttoSignedVote(vote, signer.sign(vote))

            assertEquals(signedVote, AttoSignedVote.fromBuffer(signedVote.toBuffer()))
        }

    @Test
    fun `should reject trailing signed vote bytes`() =
        runTest {
            val signedVote = AttoSignedVote(vote, signer.sign(vote))
            val buffer = signedVote.toBuffer()
            buffer.write(byteArrayOf(0))

            assertFailsWith<IllegalArgumentException> {
                AttoSignedVote.fromBuffer(buffer)
            }
        }

    @Test
    fun `should reject trailing vote bytes`() {
        val buffer = vote.toBuffer()
        buffer.write(byteArrayOf(0))

        assertFailsWith<IllegalArgumentException> {
            AttoVote.fromBuffer(buffer)
        }
    }
}
