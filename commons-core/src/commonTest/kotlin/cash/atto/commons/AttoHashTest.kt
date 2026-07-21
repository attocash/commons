package cash.atto.commons

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun `should hash supported blake2b sizes`() {
        // given
        val input = "atto".encodeToByteArray()
        val expectedBySize =
            mapOf(
                8 to "41F1A46DC16757D6",
                32 to "E389A51DF4287F19892AEABC3301175BFDE4ACA5873A0EFFD9473EFC27A4800D",
                64 to
                    "AAEBE40C5EB8AA6E780A7B1AB57DB4DB1182C1EB9B9B9B0FE2355E447064781B" +
                    "AE1519A9C550BAA7993F8050691DFC1EB91180CEC7E6108DF12EDFB52F42021E",
            )

        // when / then
        expectedBySize.forEach { (size, expected) ->
            assertEquals(expected, AttoHash.hash(size, input).value.toHex())
        }
    }

    @Test
    fun `should hash multiple chunks`() {
        // given
        val expected = "08994FC1B1C06542E3EE7BC2B6BDA6905538303D5755C0DAD714810260B7D078"

        // when
        val hash = AttoHash.hash(32, "atto".encodeToByteArray(), "commons".encodeToByteArray())

        // then
        assertEquals(expected, hash.value.toHex())
    }
}
