@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import cash.atto.commons.serialiazers.AttoSignatureAsByteArraySerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

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
    fun `should sign`() {
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
    fun `should sign 16 bytes`() {
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

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `should serialize protobuf`() {
        // given
        val expectedProtobuf =
            "0A40E9DBF6ED1F41864C3903FFAC35E2A9205601A64077F3FBCDC5D66E02E400DFD8BBD6BE6EC500BAE9B0F536E03AE0983402F00D316B8FF8540E1601AA556364A3"

        // when
        val holder = ProtoBuf.decodeFromHexString<Holder>(expectedProtobuf)
        val protobuf = ProtoBuf.encodeToHexString(holder).uppercase()

        // then
        assertEquals(expectedProtobuf, protobuf)
    }

    @Serializable
    private data class Holder(
        @Serializable(with = AttoSignatureAsByteArraySerializer::class)
        val signature: AttoSignature,
    )
}
