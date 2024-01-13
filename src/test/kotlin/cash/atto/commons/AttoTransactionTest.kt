@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import cash.atto.commons.serialiazers.json.AttoJson
import cash.atto.commons.serialiazers.protobuf.AttoProtobuf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AttoTransactionTest {

    @Test
    fun `should serialize json`() {
        // given
        val expectedTransaction = AttoTransaction(
            block = receiveBlock,
            signature = AttoSignature(Random.nextBytes(ByteArray(64))),
            work = AttoWork(Random.nextBytes(ByteArray(8)))
        )

        // when
        val json = AttoJson.encodeToString(expectedTransaction)
        val transaction = AttoJson.decodeFromString<AttoTransaction>(json)

        // then
        assertEquals(expectedTransaction, transaction)
    }

    @Test
    fun `should serialize protobuf`() {
        // given
        val expectedTransaction = AttoTransaction(
            block = receiveBlock,
            signature = AttoSignature(Random.nextBytes(ByteArray(64))),
            work = AttoWork(Random.nextBytes(ByteArray(8)))
        )

        // when
        val protobuf = AttoProtobuf.encodeToByteArray(expectedTransaction)
        val transaction = AttoProtobuf.decodeFromByteArray<AttoTransaction>(protobuf)

        // then
        assertEquals(expectedTransaction, transaction)
    }
    
}