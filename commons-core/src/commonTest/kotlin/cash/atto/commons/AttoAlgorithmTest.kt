@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

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
