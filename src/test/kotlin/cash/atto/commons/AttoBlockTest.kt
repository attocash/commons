@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import cash.atto.commons.serialiazers.json.AttoJson
import cash.atto.commons.serialiazers.protobuf.AttoProtobuf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

class AttoBlockTest {
    @ParameterizedTest
    @MethodSource("blockProvider")
    fun `should validate`(block: AttoBlock) {
        assertTrue(block.isValid())
    }

    @ParameterizedTest
    @MethodSource("invalidBlockProvider")
    fun `should not validate`(block: AttoBlock) {
        assertFalse(block.isValid())
    }

    @ParameterizedTest
    @MethodSource("blockProvider")
    fun `should serialize and deserialize protobuf`(expectedBlock: AttoBlock) {
        // when
        val byteArray = AttoProtobuf.encodeToByteArray(AttoBlock.serializer(), expectedBlock)
        val block = AttoProtobuf.decodeFromByteArray<AttoBlock>(byteArray)

        // then
        assertEquals(expectedBlock, block)
    }

    @ParameterizedTest
    @MethodSource("protobufBlockProvider")
    fun `should deserialize protobuf`(expectedProtobuf: String) {
        // when
        val block = AttoProtobuf.decodeFromHexString<AttoBlock>(expectedProtobuf)
        val protoBuf = AttoProtobuf.encodeToHexString(AttoBlock.serializer(), block).uppercase()

        // then
        assertEquals(expectedProtobuf, protoBuf)
    }

    @ParameterizedTest
    @MethodSource("blockProvider")
    fun `should serialize and deserialize json`(expectedBlock: AttoBlock) {
        // when
        val json = AttoJson.encodeToString(AttoBlock.serializer(), expectedBlock)
        val block = AttoJson.decodeFromString<AttoBlock>(json)

        // then
        assertEquals(expectedBlock, block)
    }

    @ParameterizedTest
    @MethodSource("jsonBlockProvider")
    fun `should deserialize json`(expectedJson: String) {
        // when
        val block = AttoJson.decodeFromString<AttoBlock>(expectedJson)
        val json = AttoJson.encodeToString(AttoBlock.serializer(), block)

        // then
        assertEquals(expectedJson.compactJson(), json)
    }

    @ParameterizedTest
    @MethodSource("blockProvider")
    fun `should serialize and deserialize bytes`(expectedBlock: AttoBlock) {
        // when
        val bytes = expectedBlock.toByteBuffer()
        val block = AttoBlock.fromByteBuffer(bytes)

        // then
        assertEquals(expectedBlock, block)
    }

    companion object {
        @JvmStatic
        fun blockProvider(): Stream<Arguments> =
            Stream.of(
                Arguments.of(sendBlock),
                Arguments.of(receiveBlock),
                Arguments.of(openBlock),
                Arguments.of(changeBlock),
            )

        @JvmStatic
        @Suppress("ktlint:standard:max-line-length")
        fun protobufBlockProvider(): Stream<Arguments> =
            Stream.of(
                // SEND
                Arguments.of(
                    "0A0453454E4412790000080012207D5CDF988DA53D15A3054F6749906EBF9CFE195A1F2025BA6F3F3E7F2C81E6041802200128E69792BCDB313220B1DE5FE98BB59DFE0183FF52BB567F07823ACF86F9FE633DB8A90529F26BD35438004220CC468959BCCBC42FC3BD8A93C04AA50320F606367818A30732674DD20F366C944801",
                ),
                // RECEIVE
                Arguments.of(
                    "0A07524543454956451280010000080012206B65EA9E2E4DDA6B04609A954B7697D250F92499757D6A089BCA1EA6FE550B4C1802208080A0A89C94B6E6F90128E99792BCDB3132207291794A8F931296B72D26E75E81065388D229479FFFED3E3F963DC3E36D94A438004220095D9B55A87524BAFAE1444BABC6C62DAFC411DD7E133EE237AD7893373A54B3",
                ),
                // OPEN
                Arguments.of(
                    "0A044F50454E127E000008001220CB59836A90E788AADDB2E88144423164F461282B69C490318C59CD55999C8BD0188080A0A89C94B6E6F90120889492BCDB31280032201D2BBD275B0B67847E00D05904EBD7C7D2AE41B9448CC168D4219B7A52D8D12E3A20D066813BB8D55EABC4B15B59F327C8D2D58DDBF2AD5B65B8A427267767EE79FD",
                ),
                // CHANGE
                Arguments.of(
                    "0A064348414E4745127E0000080012208E9F33C225DC8A8A2CCC15A1E8D7004C5B348CF61F83F8849C0BE6F6BC759E951802208080A0A89C94B6E6F90128ED9792BCDB313220D322600CF2C5A7A909FC1D32DB3DEA8564F660D1C09D91DC5157E8E1E664FBEC3A200FE0E4FC93DACCA9008118DC0D7FC4F0E0225A7E02AE0FAEE17B8752E99FE04E",
                ),
            )

        @JvmStatic
        fun jsonBlockProvider(): Stream<Arguments> =
            @Suppress("ktlint:standard:max-line-length")
            Stream.of(
                Arguments.of(
                    """
                    {
                       "type":"SEND",
                       "version":0,
                       "algorithm":"V1",
                       "publicKey":"A5E7E4B3B93150314E1177D5B9DE0057626B16A4B3C3F1DB37DF67628A5EF457",
                       "height":2,
                       "balance":1,
                       "timestamp":1704616009211,
                       "previous":"6CC2D3A7513723B1BA59DE784BA546BAF6447464D0BA3D80004752D6F9F4BA23",
                       "receiverAlgorithm":"V1",
                       "receiverPublicKey":"552254E101B51B22080D084C12C94BF7DFC5BE0D973025D62C0BC1FF4D9B145F",
                       "amount":1
                    }
                    """,
                ),
                Arguments.of(
                    """
                    {
                       "type":"RECEIVE",
                       "version":0,
                       "algorithm":"V1",
                       "publicKey":"39B56483A0DE38D9578CAF7EA791C2FEC96B318C7BD9989207B575334C5D9F1B",
                       "height":2,
                       "balance":18000000000000000000,
                       "timestamp":1704616009216,
                       "previous":"03783A08F51486A66A602439D9164894F07F150B548911086DAE4E4F57A9C4DD",
                       "sendHashAlgorithm":"V1",
                       "sendHash":"EE5FDA9A1ACEC7A09231792C345CDF5CD29F1059E5C413535D9FCA66A1FB2F49"
                    }
                    """,
                ),
                Arguments.of(
                    """
                   {
                       "type":"OPEN",
                       "version":0,
                       "algorithm":"V1",
                       "publicKey":"15625A4831C8F1312F1DB41550D0FD6C730FCC259ACE0FF88B500EA96783A348",
                       "balance":18000000000000000000,
                       "timestamp":1704616008836,
                       "sendHashAlgorithm":"V1",
                       "sendHash":"4DC7257C0F492B8C7AC2D8DE4A6DC4078B060BB42FDB6F8032A839AAA9048DB0",
                       "representative":"69C010A8A74924D083D1FC8234861B4B357530F42341484B4EBDA6B99F047105"
                    }
                    """,
                ),
                Arguments.of(
                    """
                    {
                       "type":"CHANGE",
                       "version":0,
                       "algorithm":"V1",
                       "publicKey":"2415EE860847B3A1CE8B605267E83481D8426A4C42F8128EA72D72F0AD072DCC",
                       "height":2,
                       "balance":18000000000000000000,
                       "timestamp":1704616009221,
                       "previous":"AD675BD718F3D96F9B89C58A8BF80741D5EDB6741D235B070D56E84098894DD5",
                       "representative":"020552FC788288CCDC239AE862A75A6B9BA62883EC099B7D6E5DFAA97C044BBB"
                    }
                    """,
                ),
            )

        @JvmStatic
        fun invalidBlockProvider(): Stream<Arguments> =
            Stream.of(
                // unknown version
                Arguments.of(sendBlock.copy(version = UShort.MAX_VALUE)),
                // future timestamp
                Arguments.of(receiveBlock.copy(timestamp = Clock.System.now().plus(1.days))),
                // invalid height
                Arguments.of(sendBlock.copy(height = 0U)),
                // invalid height
                Arguments.of(receiveBlock.copy(height = 0U)),
                // invalid height
                Arguments.of(changeBlock.copy(height = 0U)),
                // zero amount
                Arguments.of(sendBlock.copy(amount = AttoAmount.MIN)),
                // self send
                Arguments.of(sendBlock.copy(receiverPublicKey = sendBlock.publicKey)),
                // zero balance
                Arguments.of(receiveBlock.copy(balance = AttoAmount.MIN)),
                // unknown account algorithm
                Arguments.of(openBlock.copy(algorithm = AttoAlgorithm.UNKNOWN)),
                // unknown account algorithm
                Arguments.of(sendBlock.copy(algorithm = AttoAlgorithm.UNKNOWN)),
                // unknown account algorithm
                Arguments.of(receiveBlock.copy(algorithm = AttoAlgorithm.UNKNOWN)),
                // unknown account algorithm
                Arguments.of(changeBlock.copy(algorithm = AttoAlgorithm.UNKNOWN)),
                // unknown send algorithm
                Arguments.of(openBlock.copy(sendHashAlgorithm = AttoAlgorithm.UNKNOWN)),
                // unknown send algorithm
                Arguments.of(receiveBlock.copy(sendHashAlgorithm = AttoAlgorithm.UNKNOWN)),
                // unknown receiver algorithm
                Arguments.of(sendBlock.copy(receiverAlgorithm = AttoAlgorithm.UNKNOWN)),
            )
    }
}

val openBlock =
    AttoOpenBlock(
        version = 0U,
        algorithm = AttoAlgorithm.V1,
        publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
        balance = AttoAmount.MAX,
        timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
        sendHashAlgorithm = AttoAlgorithm.V1,
        sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32))),
        representative = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    )

val sendBlock =
    AttoSendBlock(
        version = 0U,
        algorithm = AttoAlgorithm.V1,
        publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
        height = 2U,
        balance = AttoAmount(1U),
        timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
        previous = AttoHash(Random.Default.nextBytes(ByteArray(32))),
        receiverAlgorithm = AttoAlgorithm.V1,
        receiverPublicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
        amount = AttoAmount(1U),
    )

val receiveBlock =
    AttoReceiveBlock(
        version = 0U,
        algorithm = AttoAlgorithm.V1,
        publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
        height = 2U,
        balance = AttoAmount.MAX,
        timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
        previous = AttoHash(Random.nextBytes(ByteArray(32))),
        sendHashAlgorithm = AttoAlgorithm.V1,
        sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32))),
    )

val changeBlock =
    AttoChangeBlock(
        version = 0U,
        algorithm = AttoAlgorithm.V1,
        publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
        height = 2U,
        balance = AttoAmount.MAX,
        timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
        previous = AttoHash(Random.nextBytes(ByteArray(32))),
        representative = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    )
