@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class AttoBlockTest {
    @Test
    fun `should validate`() {
        validBlocks.forEach {
            assertTrue(it.toString()) { it.isValid() }
        }
    }

    @Test
    fun `should not validate`() {
        invalidBlocks.forEach {
            assertFalse(it.toString()) { it.key.isValid() }
            val error = it.key.validate().getError()!!
            assertTrue(error) { error.startsWith(it.value) }
        }
    }

    @Test
    fun `should serialize and deserialize json`() {
        validBlocks.forEach { expectedBlock ->
            // when
            val json = Json.encodeToString(AttoBlock.serializer(), expectedBlock)
            val block = Json.decodeFromString<AttoBlock>(json)

            // then
            assertEquals(expectedBlock, block)
        }
    }

    @Test
    fun `should deserialize json`() {
        validJsonBlocks.forEach { expectedJson ->
            // when
            val block = Json.decodeFromString<AttoBlock>(expectedJson)
            val json = Json.encodeToString(AttoBlock.serializer(), block)

            // then
            assertEquals(expectedJson.compactJson(), json)
        }
    }

    @Test
    fun `should serialize and deserialize bytes`() {
        validBlocks.forEach { expectedBlock ->
            // when
            val bytes = expectedBlock.toBuffer()
            val block = AttoBlock.fromBuffer(bytes)

            // then
            assertEquals(expectedBlock, block)
        }
    }

    companion object {
        val openBlock =
            AttoOpenBlock(
                version = 0U.toAttoVersion(),
                network = AttoNetwork.LOCAL,
                algorithm = AttoAlgorithm.V1,
                publicKey = AttoPublicKey(Random.nextBytes(ByteArray(32))),
                balance = AttoAmount.MAX,
                timestamp = AttoInstant.now(),
                sendHashAlgorithm = AttoAlgorithm.V1,
                sendHash = AttoHash(Random.nextBytes(ByteArray(32))),
                representativeAlgorithm = AttoAlgorithm.V1,
                representativePublicKey = AttoPublicKey(Random.nextBytes(ByteArray(32))),
            )

        val sendBlock =
            AttoSendBlock(
                version = 0U.toAttoVersion(),
                network = AttoNetwork.LOCAL,
                algorithm = AttoAlgorithm.V1,
                publicKey = AttoPublicKey(Random.nextBytes(ByteArray(32))),
                height = 2U.toAttoHeight(),
                balance = AttoAmount(1U),
                timestamp = AttoInstant.now(),
                previous = AttoHash(Random.nextBytes(ByteArray(32))),
                receiverAlgorithm = AttoAlgorithm.V1,
                receiverPublicKey = AttoPublicKey(Random.nextBytes(ByteArray(32))),
                amount = AttoAmount(1U),
            )

        val receiveBlock =
            AttoReceiveBlock(
                version = 0U.toAttoVersion(),
                network = AttoNetwork.LOCAL,
                algorithm = AttoAlgorithm.V1,
                publicKey = AttoPublicKey(Random.nextBytes(ByteArray(32))),
                height = 2U.toAttoHeight(),
                balance = AttoAmount.MAX,
                timestamp = AttoInstant.now(),
                previous = AttoHash(Random.nextBytes(ByteArray(32))),
                sendHashAlgorithm = AttoAlgorithm.V1,
                sendHash = AttoHash(Random.nextBytes(ByteArray(32))),
            )

        val changeBlock =
            AttoChangeBlock(
                version = 0U.toAttoVersion(),
                network = AttoNetwork.LOCAL,
                algorithm = AttoAlgorithm.V1,
                publicKey = AttoPublicKey(Random.nextBytes(ByteArray(32))),
                height = 2U.toAttoHeight(),
                balance = AttoAmount.MAX,
                timestamp = AttoInstant.now(),
                previous = AttoHash(Random.nextBytes(ByteArray(32))),
                representativeAlgorithm = AttoAlgorithm.V1,
                representativePublicKey = AttoPublicKey(Random.nextBytes(ByteArray(32))),
            )

        val validBlocks =
            arrayOf(
                sendBlock,
                receiveBlock,
                receiveBlock.copy(timestamp = AttoInstant.now() + 1.minutes),
                openBlock,
                changeBlock,
            )

        val invalidBlocks =
            mapOf(
                // unknown version
                sendBlock.copy(version = UShort.MAX_VALUE.toAttoVersion()) as AttoBlock to "Invalid version: version=65535 > max=0",
                receiveBlock.copy(version = UShort.MAX_VALUE.toAttoVersion()) as AttoBlock to "Invalid version: version=65535 > max=0",
                changeBlock.copy(version = UShort.MAX_VALUE.toAttoVersion()) as AttoBlock to "Invalid version: version=65535 > max=0",
                openBlock.copy(version = UShort.MAX_VALUE.toAttoVersion()) as AttoBlock to "Invalid version: version=65535 > max=0",
                // future timestamp
                sendBlock.copy(timestamp = AttoInstant.now() + 1.days) as AttoBlock to "Timestamp too far in the future",
                receiveBlock.copy(timestamp = AttoInstant.now() + 1.days) as AttoBlock to "Timestamp too far in the future",
                changeBlock.copy(timestamp = AttoInstant.now() + 1.days) as AttoBlock to "Timestamp too far in the future",
                openBlock.copy(timestamp = AttoInstant.now() + 1.days) as AttoBlock to "Timestamp too far in the future",
                // invalid height
                sendBlock.copy(height = 0U.toAttoHeight()) as AttoBlock to "Height must be greater than 1: height=0",
                receiveBlock.copy(height = 1U.toAttoHeight()) as AttoBlock to "Height must be greater than 1: height=1",
                changeBlock.copy(height = 1U.toAttoHeight()) as AttoBlock to "Height must be greater than 1: height=1",
                // send zero amount
                sendBlock.copy(amount = AttoAmount.MIN) as AttoBlock to "Amount must be greater than 0",
                // self send
                sendBlock.copy(receiverPublicKey = sendBlock.publicKey) as AttoBlock to
                    "Receiver public key must be different from public key",
                // receive zero balance
                receiveBlock.copy(balance = AttoAmount.MIN) as AttoBlock to "Balance must be greater than 0",
                openBlock.copy(balance = AttoAmount.MIN) as AttoBlock to "Balance must be greater than 0",
            )

        val validJsonBlocks =
            arrayOf(
                """
            {
               "type":"SEND",
               "network":"LOCAL",
               "version":0,
               "algorithm":"V1",
               "publicKey":"A5E7E4B3B93150314E1177D5B9DE0057626B16A4B3C3F1DB37DF67628A5EF457",
               "height":2,
               "balance":1,
               "timestamp":1704616009211,
               "previous":"6CC2D3A7513723B1BA59DE784BA546BAF6447464D0BA3D80004752D6F9F4BA23",
               "receiverAlgorithm":"V1",
               "receiverPublicKey":"552254E101B51B22080D084C12C94BF7DFC5BE0D973025D62C0BC1FF4D9B145F",
               "amount":1,
               "address":"atto://acs6pzftxeyvamkocf35loo6ablwe2ywusz4h4o3g7pwoyukl32fouv6ysfxi",
               "receiverAddress":"atto://abksevhbag2rwiqibueeyewjjp357rn6bwltajowfqf4d72ntmkf7lcusv3ig"
            }
            """,
                """
            {
               "type":"RECEIVE",
               "network":"LOCAL",
               "version":0,
               "algorithm":"V1",
               "publicKey":"39B56483A0DE38D9578CAF7EA791C2FEC96B318C7BD9989207B575334C5D9F1B",
               "height":2,
               "balance":18000000000000000000,
               "timestamp":1704616009216,
               "previous":"03783A08F51486A66A602439D9164894F07F150B548911086DAE4E4F57A9C4DD",
               "sendHashAlgorithm":"V1",
               "sendHash":"EE5FDA9A1ACEC7A09231792C345CDF5CD29F1059E5C413535D9FCA66A1FB2F49",
               "address":"atto://aa43kzedudpdrwkxrsxx5j4ryl7ms2zrrr55tgesa62xkm2mlwprwr3zlywtm"
            }
            """,
                """
           {
               "type":"OPEN",
               "network":"LOCAL",
               "version":0,
               "algorithm":"V1",
               "publicKey":"15625A4831C8F1312F1DB41550D0FD6C730FCC259ACE0FF88B500EA96783A348",
               "balance":18000000000000000000,
               "timestamp":1704616008836,
               "sendHashAlgorithm":"V1",
               "sendHash":"4DC7257C0F492B8C7AC2D8DE4A6DC4078B060BB42FDB6F8032A839AAA9048DB0",
               "representativeAlgorithm":"V1",
               "representativePublicKey":"69C010A8A74924D083D1FC8234861B4B357530F42341484B4EBDA6B99F047105",
               "height":1,
               "address":"atto://aakwewsighepcmjpdw2bkugq7vwhgd6mewnm4d7yrnia5klhqorurxntbiiim",
               "representativeAddress":"atto://abu4aefiu5esjued2h6ienegdnftk5jq6qrucsclj262nom7aryql4esxmsae"
            }
            """,
                """
            {
               "type":"CHANGE",
               "network":"LOCAL",
               "version":0,
               "algorithm":"V1",
               "publicKey":"2415EE860847B3A1CE8B605267E83481D8426A4C42F8128EA72D72F0AD072DCC",
               "height":2,
               "balance":18000000000000000000,
               "timestamp":1704616009221,
               "previous":"AD675BD718F3D96F9B89C58A8BF80741D5EDB6741D235B070D56E84098894DD5",
               "representativeAlgorithm":"V1",
               "representativePublicKey":"69C010A8A74924D083D1FC8234861B4B357530F42341484B4EBDA6B99F047105",
               "address":"atto://aasbl3ugbbd3hioornqfez7igsa5qqtkjrbpqeuou4wxf4fna4w4zpqq5aol2",
               "representativeAddress":"atto://abu4aefiu5esjued2h6ienegdnftk5jq6qrucsclj262nom7aryql4esxmsae"
            }
            """,
            )
    }
}
