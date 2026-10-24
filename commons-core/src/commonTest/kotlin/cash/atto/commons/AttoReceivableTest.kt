package cash.atto.commons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class AttoReceivableTest {
    @Test
    fun `should serialize json`() {
        // given
        val expectedReceivable =
            AttoReceivable(
                network = AttoNetwork.LOCAL,
                hash = AttoHash(Random.nextBytes(32)),
                version = 0U.toAttoVersion(),
                address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.nextBytes(32))),
                timestamp = AttoInstant.now(),
                receiverAddress = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.nextBytes(32))),
                amount = AttoAmount.MAX,
            )

        // when
        val json = expectedReceivable.toJson()
        val receivable = AttoReceivable.fromJson(json)

        // then
        assertEquals(expectedReceivable, receivable)
    }

    @Test
    fun `should deserialize json`() {
        // Given
        val expectedJson =
            """
            {
               "network":"LOCAL",
               "hash":"0AF0F63BFE4DBC588F95FC3B154DE848AA9A5DD5604BAC99AE9E21C5EA8B4F64",
               "version":0,
               "algorithm":"V1",
               "publicKey":"53F1A85D25EDA5021C01A77A2B1BA99CEF9DD5FD912D7465B8B652FDEDB6A4F8",
               "timestamp":"1705517157478",
               "receiverAlgorithm":"V1",
               "receiverPublicKey":"0C400961629D759176F009249A33899440900ABCE275F6C5C01C6F7F37A2C59A",
               "amount":"18000000000000000000"
            }
            """

        // When
        val receivable = AttoReceivable.fromJson(expectedJson)
        val json = receivable.toJson()

        // Then
        assertEquals(expectedJson.compactJson(), json)
    }

    @Test
    fun `should deserialize receivable from addresses alone`() {
        // Given
        val expected = AttoBlockTest.sendBlock.toReceivable()
        val encoded = Json.parseToJsonElement(expected.toJson()).jsonObject
        val projections = setOf("algorithm", "publicKey", "receiverAlgorithm", "receiverPublicKey")
        val addressesOnly =
            JsonObject(
                encoded - projections +
                    mapOf(
                        "address" to JsonPrimitive(expected.address.toString()),
                        "receiverAddress" to JsonPrimitive(expected.receiverAddress.toString()),
                    ),
            )

        // When
        val decoded = AttoReceivable.fromJson(addressesOnly.toString())

        // Then
        assertEquals(expected, decoded)
        assertEquals(encoded, Json.parseToJsonElement(decoded.toJson()))
    }

    @Test
    fun `should ignore malformed receivable identity projections when addresses are present`() {
        // Given
        val expected = AttoBlockTest.sendBlock.toReceivable()
        val encoded = Json.parseToJsonElement(expected.toJson()).jsonObject
        val projections = setOf("algorithm", "publicKey", "receiverAlgorithm", "receiverPublicKey")
        val malformed =
            JsonObject(
                encoded + projections.associateWith { JsonObject(emptyMap()) } +
                    mapOf(
                        "address" to JsonPrimitive(expected.address.toString()),
                        "receiverAddress" to JsonPrimitive(expected.receiverAddress.toString()),
                    ),
            )

        // When
        val decoded = AttoReceivable.fromJson(malformed.toString())

        // Then
        assertEquals(expected, decoded)
        assertEquals(encoded, Json.parseToJsonElement(decoded.toJson()))
    }

    @Test
    fun `should derive receivable identity projections from copied addresses`() {
        // Given
        val original = AttoBlockTest.sendBlock.toReceivable()

        // When
        val copied = original.copy(address = original.receiverAddress, receiverAddress = original.address)

        // Then
        assertEquals(original.receiverAddress.algorithm, copied.algorithm)
        assertEquals(original.receiverAddress.publicKey, copied.publicKey)
        assertEquals(original.address.algorithm, copied.receiverAlgorithm)
        assertEquals(original.address.publicKey, copied.receiverPublicKey)
        assertEquals(copied, AttoReceivable.fromJson(copied.toJson()))
    }
}
