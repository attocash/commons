package cash.atto.commons

import cash.atto.commons.serialiazers.json.AttoJson
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AttoReceivableTest {
    @Test
    fun `should serialize json`() {
        // given
        val expectedReceivable = AttoReceivable(
            hash = AttoHash(Random.Default.nextBytes(32)),
            version = 0U,
            algorithm = AttoAlgorithm.V1,
            receiverPublicKey = AttoPublicKey(Random.Default.nextBytes(32)),
            amount = AttoAmount.MAX,
        )

        // when
        val json = AttoJson.encodeToString(expectedReceivable)
        val account = AttoJson.decodeFromString<AttoReceivable>(json)

        // then
        assertEquals(expectedReceivable, account)
    }

    @Test
    fun `should deserialize json`() {
        // given
        val expectedJson =
            """{"hash":"0AF0F63BFE4DBC588F95FC3B154DE848AA9A5DD5604BAC99AE9E21C5EA8B4F64","version":0,"algorithm":"V1","receiverPublicKey":"0C400961629D759176F009249A33899440900ABCE275F6C5C01C6F7F37A2C59A","amount":18000000000000000000}"""

        // when
        val receivable = AttoJson.decodeFromString<AttoReceivable>(expectedJson)
        val json = AttoJson.encodeToString(receivable)


        // then
        assertEquals(expectedJson, json)
    }
}