package cash.atto.commons

import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AttoTransactionTest {

    @Test
    fun serializeDeserialize() {
        // given
        val expectedBlock = AttoReceiveBlock(
            version = 0U,
            publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
            height = 1U,
            balance = AttoAmount.MAX,
            timestamp = Clock.System.now(),
            previous = AttoHash(Random.nextBytes(ByteArray(32))),
            sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32)))
        )

        val expectedTransaction = AttoTransaction(
            block = expectedBlock,
            signature = AttoSignature(Random.nextBytes(ByteArray(64))),
            work = AttoWork(Random.nextBytes(ByteArray(8)))
        )

        // when
        val json = Json.encodeToString(expectedTransaction)
        val transaction = Json.decodeFromString<AttoTransaction>(json)

        // then
        assertEquals(expectedTransaction, transaction)
    }

}