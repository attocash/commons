@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AttoAlgorithmTest {
    @Test
    fun `should serialize json`() {
        // given
        val expectedJson = "\"V1\""

        // when
        val algorithm = Json.decodeFromString<AttoAlgorithm>(expectedJson)
        val json = Json.encodeToString(algorithm)

        // then
        assertEquals(expectedJson, json)
    }
}
