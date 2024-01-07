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
import org.junit.jupiter.api.Assertions.*
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
        assertEquals(expectedJson, json)
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
        fun blockProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(sendBlock),
                Arguments.of(receiveBlock),
                Arguments.of(openBlock),
                Arguments.of(changeBlock),
            )
        }


        @JvmStatic
        fun protobufBlockProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("0A0453454E44128C01080010011A206F9FEEF09083749F65348B6710BFDE2F338E0BE171677BE23FB0699FFD1A56A9200228013218323032342D30312D30375431323A33313A30372E3337375A3A20A33381D910AF01C9DA147E5475A6C7A0DF8DDC1DB12F1B2921BE199882286C8F40014A20071A8D7F02D98C04E95367173518E81BCDF1044546B34C58E33255188DB8A4525001"),
                Arguments.of("0A0752454345495645129301080010011A202FE9B96D75CDD5D9E4BE1112F05711F0BF7D3C99C4B62D2A4B08E97BF7142D762002288080A0A89C94B6E6F9013218323032342D30312D30375431323A33313A30372E3338355A3A20D53B36322B80446B66741204BBE367AE2B820E8757513779196C4B2E0A6B0E5940014A20F446882C45EB805D7B7648CE79E9327E89B1DC9F1E49F4511A22239F455394A8"),
                Arguments.of("0A044F50454E129101080010011A20142109C57307CEE7C9C88EF598B746EBEA8BAF13A695619C9746A6613C62E4D6208080A0A89C94B6E6F9012A18323032342D30312D30375431323A33313A30362E3937355A30013A20B97C3CFFC277AE15CD077EF60EE2B8FE6476E981C29818ED591300C1030261E542207DA3875BADDB0AD238427593C29AEA0D6B860698D542DF84DCE118B8781A0602"),
                Arguments.of("0A064348414E4745129101080010011A2050AD8B1B7EA4AD6ADE44A56AC682C83421194A419E503F9403A1E8EEAD0D76FE2002288080A0A89C94B6E6F9013218323032342D30312D30375431323A33313A30372E3339315A3A20AD6E963053A076565F1D9D2AE95079BB828562F61674525E371666F8CDD4DFD54220EDE4E5E008BEFBD42BB9586CD0A21787AADEF402B65E454DDC49717A8D8EA019"),
            )
        }

        @JvmStatic
        fun jsonBlockProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    """{"type":"SEND","version":0,"algorithm":"V1","publicKey":"A5E7E4B3B93150314E1177D5B9DE0057626B16A4B3C3F1DB37DF67628A5EF457","height":2,"balance":1,"timestamp":"2024-01-07T08:26:49.211Z","previous":"6CC2D3A7513723B1BA59DE784BA546BAF6447464D0BA3D80004752D6F9F4BA23","receiverPublicKeyAlgorithm":"V1","receiverPublicKey":"552254E101B51B22080D084C12C94BF7DFC5BE0D973025D62C0BC1FF4D9B145F","amount":1}"""
                ),
                Arguments.of(
                    """{"type":"RECEIVE","version":0,"algorithm":"V1","publicKey":"39B56483A0DE38D9578CAF7EA791C2FEC96B318C7BD9989207B575334C5D9F1B","height":2,"balance":18000000000000000000,"timestamp":"2024-01-07T08:26:49.216Z","previous":"03783A08F51486A66A602439D9164894F07F150B548911086DAE4E4F57A9C4DD","sendHashAlgorithm":"V1","sendHash":"EE5FDA9A1ACEC7A09231792C345CDF5CD29F1059E5C413535D9FCA66A1FB2F49"}"""
                ),
                Arguments.of(
                    """
                    {"type":"OPEN","version":0,"algorithm":"V1","publicKey":"15625A4831C8F1312F1DB41550D0FD6C730FCC259ACE0FF88B500EA96783A348","balance":18000000000000000000,"timestamp":"2024-01-07T08:26:48.836Z","sendHashAlgorithm":"V1","sendHash":"4DC7257C0F492B8C7AC2D8DE4A6DC4078B060BB42FDB6F8032A839AAA9048DB0","representative":"69C010A8A74924D083D1FC8234861B4B357530F42341484B4EBDA6B99F047105"}""".trimIndent()
                ),
                Arguments.of(
                    """{"type":"CHANGE","version":0,"algorithm":"V1","publicKey":"2415EE860847B3A1CE8B605267E83481D8426A4C42F8128EA72D72F0AD072DCC","height":2,"balance":18000000000000000000,"timestamp":"2024-01-07T08:26:49.221Z","previous":"AD675BD718F3D96F9B89C58A8BF80741D5EDB6741D235B070D56E84098894DD5","representative":"020552FC788288CCDC239AE862A75A6B9BA62883EC099B7D6E5DFAA97C044BBB"}"""
                ),
            )
        }

        @JvmStatic
        fun invalidBlockProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(sendBlock.copy(version = UShort.MAX_VALUE)), // unknown version
                Arguments.of(receiveBlock.copy(timestamp = Clock.System.now().plus(1.days))), // future timestamp
                Arguments.of(sendBlock.copy(height = 0U)), // invalid height
                Arguments.of(receiveBlock.copy(height = 0U)), // invalid height
                Arguments.of(changeBlock.copy(height = 0U)), // invalid height
                Arguments.of(sendBlock.copy(amount = AttoAmount.MIN)), // zero amount
                Arguments.of(sendBlock.copy(receiverPublicKey = sendBlock.publicKey)), // self send
                Arguments.of(receiveBlock.copy(balance = AttoAmount.MIN)), // zero balance
                Arguments.of(openBlock.copy(algorithm = AttoAlgorithm.UNKNOWN)), // unknown account algorithm
                Arguments.of(sendBlock.copy(algorithm = AttoAlgorithm.UNKNOWN)), // unknown account algorithm
                Arguments.of(receiveBlock.copy(algorithm = AttoAlgorithm.UNKNOWN)), // unknown account algorithm
                Arguments.of(changeBlock.copy(algorithm = AttoAlgorithm.UNKNOWN)), // unknown account algorithm
                Arguments.of(openBlock.copy(sendHashAlgorithm = AttoAlgorithm.UNKNOWN)), // unknown send algorithm
                Arguments.of(receiveBlock.copy(sendHashAlgorithm = AttoAlgorithm.UNKNOWN)), // unknown send algorithm
                Arguments.of(sendBlock.copy(receiverPublicKeyAlgorithm = AttoAlgorithm.UNKNOWN)), // unknown receiver algorithm
            )
        }
    }
}


val openBlock = AttoOpenBlock(
    version = 0U,
    algorithm = AttoAlgorithm.V1,
    publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    balance = AttoAmount.MAX,
    timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
    sendHashAlgorithm = AttoAlgorithm.V1,
    sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32))),
    representative = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
)

val sendBlock = AttoSendBlock(
    version = 0U,
    algorithm = AttoAlgorithm.V1,
    publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    height = 2U,
    balance = AttoAmount(1U),
    timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
    previous = AttoHash(Random.Default.nextBytes(ByteArray(32))),
    receiverPublicKeyAlgorithm = AttoAlgorithm.V1,
    receiverPublicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    amount = AttoAmount(1U),
)

val receiveBlock = AttoReceiveBlock(
    version = 0U,
    algorithm = AttoAlgorithm.V1,
    publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    height = 2U,
    balance = AttoAmount.MAX,
    timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
    previous = AttoHash(Random.nextBytes(ByteArray(32))),
    sendHashAlgorithm = AttoAlgorithm.V1,
    sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32)))
)

val changeBlock = AttoChangeBlock(
    version = 0U,
    algorithm = AttoAlgorithm.V1,
    publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    height = 2U,
    balance = AttoAmount.MAX,
    timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
    previous = AttoHash(Random.nextBytes(ByteArray(32))),
    representative = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
)