package cash.atto.commons

import cash.atto.commons.serialiazers.json.AttoJson
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AttoAccountTest {
    @Test
    fun `should serialize json`() {
        // given
        val expectedAccount =
            AttoAccount(
                publicKey = AttoPublicKey(Random.Default.nextBytes(32)),
                version = 0U,
                algorithm = AttoAlgorithm.V1,
                height = 1U,
                balance = AttoAmount.MAX,
                lastTransactionHash = AttoHash(Random.Default.nextBytes(32)),
                lastTransactionTimestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
                representative = AttoPublicKey(Random.Default.nextBytes(32)),
            )

        // when
        val json = AttoJson.encodeToString(expectedAccount)
        val account = AttoJson.decodeFromString<AttoAccount>(json)

        // then
        assertEquals(expectedAccount, account)
    }

    @Test
    fun `should deserialize json`() {
        // given
        val expectedJson =
            """
                {
                   "publicKey":"45B3B58C26181580EEAFC1791046D54EEC2854BF550A211E2362761077D6590C",
                   "version":0,
                   "algorithm":"V1",
                   "height":1,
                   "balance":18000000000000000000,
                   "lastTransactionHash":"70F9406609BCB2E3E18F22BD0839C95E5540E95489DC6F24DBF6A1F7CFD83A92",
                   "lastTransactionTimestamp":1705517157478,
                   "representative":"99E439410A4DDD2A3A8D0B667C7A090286B8553378CF3C7AA806C3E60B6C4CBE"
                }
          """

        // when
        val account = AttoJson.decodeFromString<AttoAccount>(expectedJson)
        val json = AttoJson.encodeToString(account)

        // then
        assertEquals(expectedJson.compactJson(), json)
    }
}
