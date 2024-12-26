package cash.atto.commons

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class AttoSignatureTest {
    private val privateKey = AttoPrivateKey("00".repeat(32).fromHexToByteArray())
    private val publicKey = privateKey.toPublicKey()
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
            // when
            val signature = privateKey.sign(hash)

            // then
            assertEquals(expectedSignature, signature)
            assertTrue(expectedSignature.isValid(publicKey, hash))
        }

    @Test
    fun `should not validate wrong signature`() {
        // given
        val randomSignature = AttoSignature(Random.nextBytes(ByteArray(64)))

        // then
        assertFalse(randomSignature.isValid(publicKey, hash))
    }

    @Test
    fun `should sign 16 bytes`() =
        runTest {
            // given
            val hash16 = AttoHash(Random.nextBytes(ByteArray(16)))

            // when
            val signature = privateKey.sign(hash16)

            // then
            assertTrue(signature.isValid(publicKey, hash16))
        }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `should serialize json`() {
        // given
        val expectedJson =
            "\"B2DE82D68C24A618B4B3C5077F90779757B071737108FB7B131AB658320B8347DC9530DB0277D7802FE05C9EE5E845ED5D7D9E8B6812D55051F89B2C7084B584\""

        // when
        val signature = Json.decodeFromString(AttoSignatureSerializer, expectedJson)
        val json = Json.encodeToString(AttoSignatureSerializer, signature)

        // then
        assertEquals(expectedJson, json)
    }
}
