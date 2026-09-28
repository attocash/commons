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
    fun `should reject small-order public keys and signature points`() =
        runTest {
            // given
            val message = "atto auth challenge".encodeToByteArray()
            val validSignature = privateKey.toSigner().signMessage(message)
            val validPublicKey = privateKey.toPublicKey()
            val challenge = AttoChallenge.generate()
            val timestamp = AttoInstant.now()
            val smallOrderPoints =
                listOf(
                    "00".repeat(32),
                    "00".repeat(31) + "80",
                    "01" + "00".repeat(31),
                    "EC" + "FF".repeat(30) + "7F",
                    "C7176A703D4DD84FBA3C0B760D10670F2A2053FA2C39CCC64EC7FD7792AC037A",
                    "C7176A703D4DD84FBA3C0B760D10670F2A2053FA2C39CCC64EC7FD7792AC03FA",
                    "26E8958FC2B227B045C3F489F2EF98F0D5DFAC05D3C63339B13802886D53FC05",
                    "26E8958FC2B227B045C3F489F2EF98F0D5DFAC05D3C63339B13802886D53FC85",
                )

            // when / then
            for (encodedPoint in smallOrderPoints) {
                val point = encodedPoint.fromHexToByteArray()
                val weakPublicKey = AttoPublicKey(point)
                val weakSignature = AttoSignature(validSignature.value.copyOf().apply { point.copyInto(this) })
                assertFalse(point.passesEd25519PointEncodingPrechecks())
                assertFalse(weakSignature.passesVerificationPrechecks())
                assertFalse(validSignature.isValid(weakPublicKey, hash))
                assertFalse(validSignature.isValid(weakPublicKey, challenge, timestamp))
                assertFalse(validSignature.isValidMessage(weakPublicKey, message))
                assertFalse(weakSignature.isValid(validPublicKey, hash))
                assertFalse(weakSignature.isValid(validPublicKey, challenge, timestamp))
                assertFalse(weakSignature.isValidMessage(validPublicKey, message))
            }
        }

    @Test
    fun `should reject a signature with an identity R that satisfies the verification equation`() =
        runTest {
            // given
            // R is the identity point; S was computed for the zero hash and the test key.
            val signature =
                AttoSignature.parse(
                    "01" + "00".repeat(31) + "5F3AB63ABC9C2628EE512577C283838D2D76CC6B82B95658F94E89A0A69AA20A",
                )

            // when
            val valid = signature.isValid(privateKey.toPublicKey(), hash)

            // then
            assertFalse(valid)
        }

    @Test
    fun `should reject an identity public key and signature for different messages`() =
        runTest {
            // given
            val publicKey = AttoPublicKey.parse("01" + "00".repeat(31))
            val signature = AttoSignature.parse("01" + "00".repeat(63))
            val messages = listOf(ByteArray(0), "first message".encodeToByteArray(), "different message".encodeToByteArray())

            // when / then
            for (message in messages) {
                assertFalse(signature.isValidMessage(publicKey, message))
            }
        }

    @Test
    fun `should reject malformed public keys in all signature verification methods`() =
        runTest {
            // given
            val message = "atto auth challenge".encodeToByteArray()
            val validSignature = privateKey.toSigner().signMessage(message)
            val challenge = AttoChallenge.generate()
            val timestamp = AttoInstant.now()
            val malformedPublicKeys =
                listOf(
                    "E0EB7A7C3B41B8AE1656E3FAF19FC46ADA098DEB9C32B1FD866205165F49B800",
                    "E0EB7A7C3B41B8AE1656E3FAF19FC46ADA098DEB9C32B1FD866205165F49B880",
                    "5F9C95BCA3508C24B1D0B1559C83EF5B04445CC4581C8E86D8224EDDD09F1157",
                    "5F9C95BCA3508C24B1D0B1559C83EF5B04445CC4581C8E86D8224EDDD09F11D7",
                )

            // when / then
            for (encodedKey in malformedPublicKeys) {
                val malformedPublicKey = AttoPublicKey.parse(encodedKey)
                assertFalse(validSignature.isValid(malformedPublicKey, hash))
                assertFalse(validSignature.isValid(malformedPublicKey, challenge, timestamp))
                assertFalse(validSignature.isValidMessage(malformedPublicKey, message))
            }
        }

    @Test
    fun `should reject noncanonical message signatures`() =
        runTest {
            // given
            val message = "atto auth challenge".encodeToByteArray()
            val publicKey = privateKey.toPublicKey()
            val validSignature = privateKey.toSigner().signMessage(message)
            val challenge = AttoChallenge.generate()
            val timestamp = AttoInstant.now()
            val noncanonicalScalar = validSignature.value.copyOf()
            val groupOrder = ("EDD3F55C1A631258D69CF7A2DEF9DE14" + "00".repeat(15) + "10").fromHexToByteArray()
            groupOrder.copyInto(noncanonicalScalar, destinationOffset = 32)

            // when / then
            assertFalse(AttoSignature(ByteArray(64)).isValidMessage(publicKey, message))
            assertFalse(validSignature.isValidMessage(AttoPublicKey.parse("02" + "00".repeat(31)), message))
            assertFalse(AttoSignature(noncanonicalScalar).isValid(publicKey, hash))
            assertFalse(AttoSignature(noncanonicalScalar).isValid(publicKey, challenge, timestamp))
            assertFalse(AttoSignature(noncanonicalScalar).isValidMessage(publicKey, message))
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
