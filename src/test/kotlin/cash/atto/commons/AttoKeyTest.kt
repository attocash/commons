@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import cash.atto.commons.serialiazers.json.AttoJson
import cash.atto.commons.serialiazers.json.AttoPublicKeyJsonSerializer
import cash.atto.commons.serialiazers.protobuf.AttoProtobuf
import kotlinx.serialization.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AttoKeyTest {

    @Test
    fun `should create private key`() {
        // given
        val mnemonic =
            AttoMnemonic("edge defense waste choose enrich upon flee junk siren film clown finish luggage leader kid quick brick print evidence swap drill paddle truly occur")
        val seed = mnemonic.toSeed("some password")

        // when
        val privateKey = seed.toPrivateKey(0U)

        // then
        val expectedPrivateKey = "38FDB3EBF6B34965FFEE18583B597808B56CDA98B074405A30152E2296616B3A"
        assertEquals(expectedPrivateKey, privateKey.value.toHex())
    }

    @Test
    fun `should create public key`() {
        // given
        val mnemonic =
            AttoMnemonic("edge defense waste choose enrich upon flee junk siren film clown finish luggage leader kid quick brick print evidence swap drill paddle truly occur")
        val seed = mnemonic.toSeed("some password")
        val privateKey = seed.toPrivateKey(0U)

        // when
        val publicKey = privateKey.toPublicKey()

        // then
        val expectedPublicKey = "9979705D9F9588F46667697329947688E5FFC4DF36F5D0C6A4E29D023E7BF2CE"
        assertEquals(expectedPublicKey, publicKey.value.toHex())
    }

    @Test
    fun `should serialize json`() {
        // given
        val expectedJson = "\"67FFDB1309565DF8566D22ABFBB30E37615A401174D863821FFC3FE5C458CA8C\""

        // when
        val publicKey = AttoJson.decodeFromString(AttoPublicKeyJsonSerializer, expectedJson)
        val json = AttoJson.encodeToString(AttoPublicKeyJsonSerializer, publicKey)

        // then
        assertEquals(expectedJson, json)
    }

    @Test
    fun `should serialize protobuf`() {
        // given
        val expectedProtobuf = "0A20FD213897BE32302B06BF2B99005B05002CFAAF635391A8DBE178B32EB98A45C6"

        // when
        val holder = AttoProtobuf.decodeFromHexString<Holder>(expectedProtobuf)
        val protobuf = AttoProtobuf.encodeToHexString(holder).uppercase()

        // then
        assertEquals(expectedProtobuf, protobuf)
    }

    @Serializable
    private data class Holder(@Contextual val publicKey: AttoPublicKey)
}