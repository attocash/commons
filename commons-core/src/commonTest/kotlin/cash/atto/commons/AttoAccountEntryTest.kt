package cash.atto.commons

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class AttoAccountEntryTest {
    @Test
    fun `should serialize json`() {
        // given
        val expectedAccountEntry =
            AttoAccountEntry(
                hash = AttoHash(Random.Default.nextBytes(32)),
                address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.Default.nextBytes(32))),
                height = AttoHeight(0U),
                blockType = AttoBlockType.RECEIVE,
                subjectAddress = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.Default.nextBytes(32))),
                previousBalance = AttoAmount(0U),
                balance = AttoAmount(100U),
                timestamp = AttoInstant.now(),
            )

        // when
        val json = expectedAccountEntry.toJson()
        val accountEntry = AttoAccountEntry.fromJson(json)

        // then
        assertEquals(expectedAccountEntry, accountEntry)
    }

    @Test
    fun `should deserialize json`() {
        // Given
        val expectedJson =
            """
            {
               "hash":"68BA42CDD87328380BE32D5AA6DBB86E905B50273D37AF1DE12F47B83A001154",
               "algorithm":"V1",
               "publicKey":"FD595851104FDDB2FEBF3739C8006C8AAE9B8A2B1BC390D5FDF07EBDD8583FA1",
               "height":"0",
               "blockType":"RECEIVE",
               "subjectAlgorithm":"V1",
               "subjectPublicKey":"2EB21717813E7A0E0A7E308B8E2FD8A051F8724F5C5F0047E92E19310C582E3A",
               "previousBalance":"0",
               "balance":"100",
               "timestamp":"1704616009211"
            }
            """

        // When
        val accountEntry = AttoAccountEntry.fromJson(expectedJson)
        val json = accountEntry.toJson()

        // Then
        assertEquals(expectedJson.compactJson(), json)
    }

    @Test
    fun `should deserialize account entry from addresses alone`() {
        // Given
        val expected = accountEntry()
        val legacyJson = Json.parseToJsonElement(expected.toJson()).jsonObject
        val addressJson =
            JsonObject(
                legacyJson - setOf("algorithm", "publicKey", "subjectAlgorithm", "subjectPublicKey") +
                    mapOf(
                        "address" to JsonPrimitive(expected.address.toString()),
                        "subjectAddress" to JsonPrimitive(expected.subjectAddress.toString()),
                    ),
            )

        // When
        val accountEntry = AttoAccountEntry.fromJson(addressJson.toString())

        // Then
        assertEquals(expected, accountEntry)
        assertEquals(legacyJson, Json.parseToJsonElement(accountEntry.toJson()))
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `should include matching address defaults when requested`() {
        // Given
        val expected = accountEntry()
        val json = Json { encodeDefaults = true }
        val legacyJson = Json.parseToJsonElement(expected.toJson()).jsonObject

        // When
        val encodedJson = json.encodeToString(expected)
        val encoded = Json.parseToJsonElement(encodedJson).jsonObject
        val decoded = json.decodeFromString<AttoAccountEntry>(encodedJson)

        // Then
        assertEquals(JsonPrimitive(expected.address.toString()), encoded["address"])
        assertEquals(JsonPrimitive(expected.subjectAddress.toString()), encoded["subjectAddress"])
        assertEquals(
            JsonObject(
                legacyJson +
                    mapOf(
                        "address" to JsonPrimitive(expected.address.toString()),
                        "subjectAddress" to JsonPrimitive(expected.subjectAddress.toString()),
                    ),
            ),
            encoded,
        )
        assertEquals(expected, decoded)
        assertEquals("cash.atto.commons.AttoAccountEntry", AttoAccountEntry.serializer().descriptor.serialName)
    }

    @Test
    fun `should ignore malformed legacy identity projections when addresses are present`() {
        // Given
        val expected = accountEntry()
        val encoded = Json.parseToJsonElement(expected.toJson()).jsonObject
        val projections = setOf("algorithm", "publicKey", "subjectAlgorithm", "subjectPublicKey")
        val malformed =
            JsonObject(
                encoded + projections.associateWith { JsonObject(emptyMap()) } +
                    mapOf(
                        "address" to JsonPrimitive(expected.address.toString()),
                        "subjectAddress" to JsonPrimitive(expected.subjectAddress.toString()),
                    ),
            )

        // When
        val decoded = AttoAccountEntry.fromJson(malformed.toString())

        // Then
        assertEquals(expected, decoded)
        assertEquals(encoded, Json.parseToJsonElement(decoded.toJson()))
    }

    @Test
    fun `should derive legacy projections from copied addresses`() {
        // Given
        val original = accountEntry()
        val newAddress = original.subjectAddress
        val newSubjectAddress = original.address

        // When
        val copied = original.copy(address = newAddress, subjectAddress = newSubjectAddress)

        // Then
        assertEquals(newAddress.algorithm, copied.algorithm)
        assertEquals(newAddress.publicKey, copied.publicKey)
        assertEquals(newSubjectAddress.algorithm, copied.subjectAlgorithm)
        assertEquals(newSubjectAddress.publicKey, copied.subjectPublicKey)
        assertEquals(original.hash, copied.hash)
        assertEquals(original.height, copied.height)
        assertEquals(original.blockType, copied.blockType)
        assertEquals(original.previousBalance, copied.previousBalance)
        assertEquals(original.balance, copied.balance)
        assertEquals(original.timestamp, copied.timestamp)
        assertEquals(original, AttoAccountEntry.fromJson(original.toJson()))
        assertEquals(copied, AttoAccountEntry.fromJson(copied.toJson()))
    }

    private fun accountEntry(): AttoAccountEntry {
        val block = AttoBlockTest.sendBlock
        return AttoAccountEntry(
            hash = block.hash,
            address = block.address,
            height = block.height,
            blockType = block.type,
            subjectAddress = block.receiverAddress,
            previousBalance = block.balance + block.amount,
            balance = block.balance,
            timestamp = block.timestamp,
        )
    }
}
