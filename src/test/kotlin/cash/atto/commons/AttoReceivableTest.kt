package cash.atto.commons

import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AttoReceivableTest {
    @Test
    fun `should serialize json`() {
        // given
        val expectedReceivable =
            AttoReceivable(
                hash = AttoHash(Random.Default.nextBytes(32)),
                version = 0U.toAttoVersion(),
                algorithm = AttoAlgorithm.V1,
                timestamp = Clock.System.now(),
                receiverAlgorithm = AttoAlgorithm.V1,
                receiverPublicKey = AttoPublicKey(Random.Default.nextBytes(32)),
                amount = AttoAmount.MAX,
            )

        // when
        val json = Json.encodeToString(expectedReceivable)
        val account = Json.decodeFromString<AttoReceivable>(json)

        // then
        assertEquals(expectedReceivable, account)
    }

    @Test
    fun `should deserialize json`() {
        // given
        val expectedJson =
            """
            {
               "hash":"0AF0F63BFE4DBC588F95FC3B154DE848AA9A5DD5604BAC99AE9E21C5EA8B4F64",
               "version":0,
               "algorithm":"V1",
               "timestamp":"2024-10-05T11:11:58.063029Z",
               "receiverAlgorithm":"V1",
               "receiverPublicKey":"0C400961629D759176F009249A33899440900ABCE275F6C5C01C6F7F37A2C59A",
               "amount":18000000000000000000
            }
            """

        // when
        val receivable = Json.decodeFromString<AttoReceivable>(expectedJson)
        val json = Json.encodeToString(receivable)

        // then
        assertEquals(expectedJson.compactJson(), json)
    }
}
