@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import cash.atto.commons.serialiazers.json.AttoJson
import cash.atto.commons.serialiazers.protobuf.AttoProtobuf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AttoAlgorithmTest {

    @Test
    fun `should serialize json`() {
        // given
        val expectedJson = "\"V1\""

        // when
        val algorithm = AttoJson.decodeFromString<AttoAlgorithm>(expectedJson)
        val json = AttoJson.encodeToString(algorithm)

        // then
        assertEquals(expectedJson, json)
    }

    @Test
    fun `should serialize protobuf`() {
        // given
        val expectedProtobuf = "00"

        // when
        val algorithm = AttoProtobuf.decodeFromHexString<AttoAlgorithm>(expectedProtobuf)
        val protobuf = AttoProtobuf.encodeToHexString(algorithm)

        // then
        assertEquals(expectedProtobuf, protobuf)
    }
}