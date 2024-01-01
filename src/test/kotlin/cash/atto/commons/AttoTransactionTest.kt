package cash.atto.commons

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AttoTransactionTest {

    @Test
    fun serializeDeserialize() {
        // given
        val expectedTransaction = AttoTransaction(
            block = receiveBlock,
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