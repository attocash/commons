package cash.atto.commons

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

internal class AttoSignatureTest {
    private val privateKey = AttoPrivateKey("00".repeat(32).fromHexToByteArray())
    private val hash = AttoHash("0000000000000000000000000000000000000000000000000000000000000000".fromHexToByteArray())

    @Suppress("ktlint:standard:max-line-length")
    private val expectedSignature =
        AttoSignature(
            "3DA1EBDFA96EDD181DBE3659D1C051C431F056A5AD6A97A60D5CCA10460438783546461E31285FC59F91C7072642745061E2451D5FF33BCCD8C3C74DABCAF60A"
                .fromHexToByteArray(),
        )

    @Test
    fun `should sign`() =
        runTest {
            val publicKey = privateKey.toPublicKey()

            // when
            val signature = privateKey.sign(hash)

            // then
            assertEquals(expectedSignature, signature)
            assertTrue(expectedSignature.isValid(publicKey, hash))
        }

    @Test
    fun `should not validate wrong signature`() =
        runTest {
            // given
            val publicKey = privateKey.toPublicKey()
            val randomSignature = AttoSignature(Random.nextBytes(ByteArray(64)))

            // then
            assertFalse(randomSignature.isValid(publicKey, hash))
        }

    @Test
    fun `should sign 16 bytes`() =
        runTest {
            // given
            val publicKey = privateKey.toPublicKey()
            val hash16 = AttoHash(Random.nextBytes(ByteArray(16)))

            // when
            val signature = privateKey.sign(hash16)

            // then
            assertTrue(signature.isValid(publicKey, hash16))
        }

    @Test
    fun `should validate timestamped challenge signature`() =
        runTest {
            val publicKey = privateKey.toPublicKey()
            val challenge = AttoChallenge.generate()
            val timestamp = AttoInstant.now()
            val signature = privateKey.toSigner().sign(challenge, timestamp)

            assertTrue(signature.isValid(publicKey, challenge, timestamp))
            assertFalse(signature.isValid(publicKey, AttoChallenge.generate(), timestamp))
            assertFalse(signature.isValid(publicKey, challenge, timestamp + 1.seconds))
            assertFalse(signature.isValid(AttoPrivateKey.generate().toPublicKey(), challenge, timestamp))
        }

    @Test
    fun `should sign message with atto domain separated hash`() =
        runTest {
            val publicKey = privateKey.toPublicKey()
            val message = "atto message".encodeToByteArray()
            val signature = privateKey.toSigner().signMessage(message)
            val signedMessageHash = attoSignedMessageHashForTest(publicKey, message)

            assertTrue(signature.isValid(publicKey, signedMessageHash))
            assertTrue(signature.isValidMessage(publicKey, message))
            assertFalse(signature.isValid(publicKey, AttoHash(message)))
        }

    @Test
    fun `should reject message signature with wrong framing inputs`() =
        runTest {
            val publicKey = privateKey.toPublicKey()
            val message = "atto message".encodeToByteArray()
            val signature = privateKey.toSigner().signMessage(message)
            val bigEndianLengthHash =
                AttoHash.hash(
                    64,
                    signedMessageDomainForTest,
                    publicKey.value,
                    message.size.toULong().toBigEndianByteArrayForTest(),
                    message,
                )

            assertFalse(signature.isValidMessage(publicKey, "other message".encodeToByteArray()))
            assertFalse(signature.isValidMessage(AttoPrivateKey.generate().toPublicKey(), message))
            assertFalse(signature.isValid(publicKey, bigEndianLengthHash))
        }

    @Test
    fun `should sign equivalent message bytes the same way`() =
        runTest {
            val signer = privateKey.toSigner()
            val utf8Signature = signer.signMessage("hello".encodeToByteArray())
            val hexSignature = signer.signMessage("68656c6c6f".fromHexToByteArray())

            assertEquals(utf8Signature, hexSignature)
        }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `should serialize json`() {
        // given
        val expectedJson =
            "\"B2DE82D68C24A618B4B3C5077F90779757B071737108FB7B131AB658320B8347DC9530DB0277D7802FE05C9EE5E845ED5D7D9E8B6812D55051F89B2C7084B584\""

        // when
        val signature = Json.decodeFromString(AttoSignatureAsStringSerializer, expectedJson)
        val json = Json.encodeToString(AttoSignatureAsStringSerializer, signature)

        // then
        assertEquals(expectedJson, json)
    }

    private fun attoSignedMessageHashForTest(
        publicKey: AttoPublicKey,
        message: ByteArray,
    ): AttoHash =
        AttoHash.hash(
            64,
            signedMessageDomainForTest,
            publicKey.value,
            message.size.toULong().toByteArray(),
            message,
        )

    private fun ULong.toBigEndianByteArrayForTest(): ByteArray =
        ByteArray(8) { index ->
            ((this shr ((7 - index) * 8)) and 0xFFU).toByte()
        }

    private companion object {
        val signedMessageDomainForTest = "ATTO Signed Message v1".encodeToByteArray()
    }
}
