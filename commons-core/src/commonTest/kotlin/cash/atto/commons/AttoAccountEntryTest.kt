package cash.atto.commons

import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class AttoAccountEntryTest {
    @Test
    fun `should serialize json`() {
        // given
        val expectedReceivable =
            AttoAccountEntry(
                hash = AttoHash(Random.Default.nextBytes(32)),
                algorithm = AttoAlgorithm.V1,
                publicKey = AttoPublicKey(Random.Default.nextBytes(32)),
                height = AttoHeight(0U),
                blockType = AttoBlockType.RECEIVE,
                subjectAlgorithm = AttoAlgorithm.V1,
                subjectPublicKey = AttoPublicKey(Random.Default.nextBytes(32)),
                previousBalance = AttoAmount(0U),
                balance = AttoAmount(100U),
                timestamp = AttoInstant.now(),
            )

        // when
        val json = Json.encodeToString(expectedReceivable)
        val account = Json.decodeFromString<AttoAccountEntry>(json)

        // then
        assertEquals(expectedReceivable, account)
    }

    @Test
    fun `should deserialize json`() {
        // given
        val expectedJson =
            """
            {
               "hash":"68BA42CDD87328380BE32D5AA6DBB86E905B50273D37AF1DE12F47B83A001154",
               "algorithm":"V1",
               "publicKey":"FD595851104FDDB2FEBF3739C8006C8AAE9B8A2B1BC390D5FDF07EBDD8583FA1",
               "height":0,
               "blockType":"RECEIVE",
               "subjectAlgorithm":"V1",
               "subjectPublicKey":"2EB21717813E7A0E0A7E308B8E2FD8A051F8724F5C5F0047E92E19310C582E3A",
               "previousBalance":0,
               "balance":100,
               "timestamp":1704616009211
            }
            """

        // when
        val receivable = Json.decodeFromString<AttoAccountEntry>(expectedJson)
        val json = Json.encodeToString(receivable)

        // then
        assertEquals(expectedJson.compactJson(), json)
    }
}
