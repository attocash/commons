package cash.atto.commons

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AttoHashTest {
    @Test
    fun `should hash`() {
        // given
        val byteArray = "0000000000000000000000000000000000000000000000000000000000000000".fromHexToByteArray()

        // when
        val hash = AttoHash.hash(32, byteArray).value.toHex()

        // then
        val expectedHash = "89EB0D6A8A691DAE2CD15ED0369931CE0A949ECAFA5C3F93F8121833646E15C3"
        assertEquals(expectedHash, hash)
    }

    @Test
    fun `should serialize json`() {
        // given
        val expectedJson = "\"67FFDB1309565DF8566D22ABFBB30E37615A401174D863821FFC3FE5C458CA8C\""

        // when
        val hash = Json.decodeFromString(AttoHash.serializer(), expectedJson)
        val json = Json.encodeToString(AttoHash.serializer(), hash)

        // then
        assertEquals(expectedJson, json)
    }
}
