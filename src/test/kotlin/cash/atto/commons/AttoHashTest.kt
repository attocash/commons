@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import cash.atto.commons.serialiazers.json.AttoHashJsonSerializer
import cash.atto.commons.serialiazers.json.AttoJson
import cash.atto.commons.serialiazers.protobuf.AttoProtobuf
import kotlinx.serialization.*
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
        val hash = AttoJson.decodeFromString(AttoHashJsonSerializer, expectedJson)
        val json = AttoJson.encodeToString(AttoHashJsonSerializer, hash)

        // then
        assertEquals(expectedJson, json)
    }

    @Test
    fun `should serialize protobuf`() {
        // given
        val expectedProtobuf = "0A20F65437187FEEB1F9BF2D4A3BEDE9BFC25746C511779E8E0CFEFBF9D610A6D8AD"

        // when
        val holder = AttoProtobuf.decodeFromHexString<Holder>(expectedProtobuf)
        val protobuf = AttoProtobuf.encodeToHexString(holder).uppercase()

        // then
        assertEquals(expectedProtobuf, protobuf)
    }

    @Serializable
    private data class Holder(
        @Contextual val hash: AttoHash,
    )
}
