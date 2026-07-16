package cash.atto.commons

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
                algorithm = AttoAlgorithm.V1,
                publicKey = AttoPublicKey(Random.nextBytes(32)),
                timestamp = AttoInstant.now(),
                receiverAlgorithm = AttoAlgorithm.V1,
                receiverPublicKey = AttoPublicKey(Random.nextBytes(32)),
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
        // given
        val expectedJson =
            """
            {
               "network":"LOCAL",
               "hash":"0AF0F63BFE4DBC588F95FC3B154DE848AA9A5DD5604BAC99AE9E21C5EA8B4F64",
               "version":0,
               "algorithm":"V1",
               "publicKey":"53F1A85D25EDA5021C01A77A2B1BA99CEF9DD5FD912D7465B8B652FDEDB6A4F8",
               "timestamp":1705517157478,
               "receiverAlgorithm":"V1",
               "receiverPublicKey":"0C400961629D759176F009249A33899440900ABCE275F6C5C01C6F7F37A2C59A",
               "amount":18000000000000000000
            }
            """

        // when
        val receivable = AttoReceivable.fromJson(expectedJson)
        val json = receivable.toJson()

        // then
        assertEquals(expectedJson.compactJson(), json)
    }
}
