package cash.atto.commons

import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AttoVoteTest {
    private val privateKey = AttoPrivateKey(ByteArray(32))

    @Test
    fun `should preserve pre address API vote bytes hash and signature`() =
        runTest {
            // Given
            // Captured with the original algorithm/publicKey constructor and serializer.
            val legacyJson =
                """
                {
                    "version": 0,
                    "algorithm": "V1",
                    "publicKey": "3B6A27BCCEB6A42D62A3A8D02A6F0D73653215771DE243A63AC048A18B59DA29",
                    "blockAlgorithm": "V1",
                    "blockHash": "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F",
                    "timestamp": "1700000000000"
                }
                """.trimIndent()
            val legacyBytes =
                (
                    "0000003b6a27bcceb6a42d62a3a8d02a6f0d73653215771de243a63ac048a18b59da29" +
                        "00000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f0068e5cf8b010000"
                ).fromHexToByteArray()
            val legacyHash = AttoHash("5f1d24862618712aeed1adc7e846af18483824e6d054c98150cb1baf4835ee4b".fromHexToByteArray())
            val legacySignature =
                AttoSignature(
                    (
                        "2b597e674a11c859253466b701f20bd9281a67bb38ba69ec1ccd9aec3815beb34" +
                            "da9f2e750801f3d29a0f892a8bccde3416104b178d416df7323823aef378802"
                    ).fromHexToByteArray(),
                )
            val legacySignedBytes = legacyBytes + legacySignature.value
            val (expected, signer) = fixture()

            // When
            val fromJson = Json.decodeFromString<AttoVote>(legacyJson)
            val fromBytes = AttoVote.fromBuffer(legacyBytes.toBuffer())
            val signedVote = AttoSignedVote.fromBuffer(legacySignedBytes.toBuffer())
            val regeneratedSignature = signer.sign(expected)

            // Then
            assertEquals(expected, fromJson)
            assertEquals(Json.encodeToString(Json.parseToJsonElement(legacyJson)), Json.encodeToString(fromJson))
            assertEquals(expected, fromBytes)
            assertEquals(expected, signedVote.vote)
            assertEquals(legacyHash, expected.hash)
            assertEquals(legacyHash, fromJson.hash)
            assertEquals(legacyHash, signedVote.hash)
            assertEquals(legacySignature, regeneratedSignature)
            assertEquals(legacySignature, signedVote.signature)
            assertContentEquals(legacyBytes, expected.toBuffer().readByteArray())
            assertContentEquals(legacyBytes, fromJson.toBuffer().readByteArray())
            assertContentEquals(legacySignedBytes, signedVote.toBuffer().readByteArray())
            assertTrue(signedVote.isValid())
            assertTrue(AttoSignedVote(fromJson, legacySignature).isValid())
        }

    @Test
    fun `should derive identity from address when copying a vote`() =
        runTest {
            // Given
            val vote = fixture().first
            val address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32) { 1 }))

            // When
            val copied = vote.copy(address = address)
            val decoded = Json.decodeFromString<AttoVote>(Json.encodeToString(copied))

            // Then
            assertEquals(address, copied.address)
            assertEquals(address.algorithm, copied.algorithm)
            assertEquals(address.publicKey, copied.publicKey)
            assertEquals(copied, decoded)
        }

    @Test
    fun `should emit only original JSON fields`() =
        runTest {
            // Given
            val vote = fixture().first

            // When
            val encoded = Json.encodeToJsonElement(AttoVote.serializer(), vote).jsonObject
            val encodedWithDefaults = Json { encodeDefaults = true }.encodeToJsonElement(AttoVote.serializer(), vote).jsonObject

            // Then
            assertEquals(
                listOf("version", "algorithm", "publicKey", "blockAlgorithm", "blockHash", "timestamp"),
                encoded.keys.toList(),
            )
            assertEquals(JsonPrimitive(vote.algorithm.name), encoded["algorithm"])
            assertEquals(JsonPrimitive(vote.publicKey.toString()), encoded["publicKey"])
            assertEquals(encoded, encodedWithDefaults)
        }

    @Test
    fun `should decode legacy and address only JSON with identical signed bytes`() =
        runTest {
            // Given
            val (vote, signer) = fixture()
            val signature = signer.sign(vote)
            val encoded = Json.encodeToJsonElement(AttoVote.serializer(), vote).jsonObject
            val legacy = encoded
            val canonical = JsonObject((encoded - setOf("algorithm", "publicKey")) + ("address" to JsonPrimitive(vote.address.value)))

            // When
            val decoded = listOf(legacy, canonical).map { Json.decodeFromJsonElement(AttoVote.serializer(), it) }

            // Then
            for (value in decoded) {
                assertEquals(vote, value)
                assertEquals(vote.hash, value.hash)
                assertContentEquals(vote.toBuffer().readByteArray(), value.toBuffer().readByteArray())
                assertTrue(AttoSignedVote(value, signature).isValid())
            }
        }

    @Test
    fun `should ignore malformed identity projections when JSON contains address`() =
        runTest {
            // Given
            val vote = fixture().first
            val encoded = Json.encodeToJsonElement(AttoVote.serializer(), vote).jsonObject
            val malformed =
                JsonObject(
                    encoded +
                        mapOf(
                            "address" to JsonPrimitive(vote.address.value),
                            "algorithm" to JsonObject(emptyMap()),
                            "publicKey" to JsonPrimitive("invalid public key"),
                        ),
                )

            // When
            val decoded = Json.decodeFromJsonElement(AttoVote.serializer(), malformed)

            // Then
            assertEquals(vote, decoded)
        }

    @Test
    fun `should reject a missing legacy identity and an invalid block algorithm`() =
        runTest {
            // Given
            val vote = fixture().first
            val encoded = Json.encodeToJsonElement(AttoVote.serializer(), vote).jsonObject
            val missingIdentity = JsonObject(encoded - setOf("address", "publicKey"))
            val invalidBlockAlgorithm = JsonObject(encoded + ("blockAlgorithm" to JsonPrimitive("invalid")))

            // When
            val missingIdentityFailure =
                runCatching { Json.decodeFromJsonElement(AttoVote.serializer(), missingIdentity) }.exceptionOrNull()
            val invalidBlockAlgorithmFailure =
                runCatching { Json.decodeFromJsonElement(AttoVote.serializer(), invalidBlockAlgorithm) }.exceptionOrNull()

            // Then
            assertIs<IllegalArgumentException>(missingIdentityFailure)
            assertIs<IllegalArgumentException>(invalidBlockAlgorithmFailure)
        }

    @Test
    fun `should serialize and deserialize signed vote bytes`() =
        runTest {
            // Given
            val (vote, signer) = fixture()
            val signedVote = AttoSignedVote(vote, signer.sign(vote))

            // When
            val decoded = AttoSignedVote.fromBuffer(signedVote.toBuffer())
            val json = Json.encodeToString(AttoSignedVoteAsByteArraySerializer, signedVote)
            val decodedJson = Json.decodeFromString(AttoSignedVoteAsByteArraySerializer, json)

            // Then
            assertEquals(signedVote, decoded)
            assertEquals(signedVote, decodedJson)
            assertEquals(signedVote.hash, decoded.hash)
            assertContentEquals(signedVote.toBuffer().readByteArray(), decoded.toBuffer().readByteArray())
            assertTrue(decoded.isValid())
            assertTrue(decodedJson.isValid())
        }

    @Test
    fun `should reject trailing signed vote bytes`() =
        runTest {
            // Given
            val (vote, signer) = fixture()
            val signedVote = AttoSignedVote(vote, signer.sign(vote))
            val buffer = signedVote.toBuffer()

            // When
            buffer.write(byteArrayOf(0))

            // Then
            assertFailsWith<IllegalArgumentException> {
                AttoSignedVote.fromBuffer(buffer)
            }
        }

    @Test
    fun `should reject trailing vote bytes`() =
        runTest {
            // Given
            val vote = fixture().first
            val buffer = vote.toBuffer()

            // When
            buffer.write(byteArrayOf(0))

            // Then
            assertFailsWith<IllegalArgumentException> {
                AttoVote.fromBuffer(buffer)
            }
        }

    private suspend fun fixture(): Pair<AttoVote, AttoSigner> {
        val publicKey = privateKey.toPublicKey()
        val signer = privateKey.toSigner()
        val vote =
            AttoVote(
                version = 0U.toAttoVersion(),
                address = AttoAddress(AttoAlgorithm.V1, publicKey),
                blockAlgorithm = AttoAlgorithm.V1,
                blockHash = AttoHash(ByteArray(32) { it.toByte() }),
                timestamp = AttoInstant.fromEpochMilliseconds(1_700_000_000_000),
            )
        return vote to signer
    }
}
