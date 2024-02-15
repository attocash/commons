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
                Arguments.of("0A0453454E44128C01000008001220B459EF920FBEB34BBA6E9B26F4ADD57471FC7ED91F23442999C5684370022A8B180220012A18323032342D30312D31335431353A34333A31352E3033375A32204D54BF95297234A404023AFC03C55CFD4EF0EFF9F2EFE80F647A72F4302EFFF7380042204CEDD6A6B3F62F830219011535EE274F191A66EC5E703CB371931D5EC723FA464801"),
                Arguments.of("0A075245434549564512930100000800122037099E5141DDD2ADB650D262BFC5CF114ADC25295278B6F10625A5D23C652AF21802208080A0A89C94B6E6F9012A18323032342D30312D31335431353A34333A31352E3034335A322019F2DD9359BA7A6D214F328AF7841E7584F40D3358F90431D14A390642C54805380042208E632A1A20A3C3F1E87A64B0DFA3162C626B710A248CD7570D1EE931138AE64B"),
                Arguments.of("0A044F50454E129101000008001220CAC6214C7A1E61A14CE6525A0CE53FEA57287FA0BAB0FCEF5DB1ECB5E57B14D8188080A0A89C94B6E6F9012218323032342D30312D31335431353A34333A31342E3636355A2800322073D9DCD411DED5BCA06A3066347C3B88C37AF2CC9EB35D68D926C918A618DB683A20BA527F3B8808043A07EE60399AD1AA1F20BA13A452881BFC9244BE8CCA15FD5E"),
                Arguments.of("0A064348414E474512910100000800122010DB1473FA5CECD5F8F9A43DBD1BA7293730EF331DC0E064CF828AD3521D6B111802208080A0A89C94B6E6F9012A18323032342D30312D31335431353A34333A31352E3034365A3220974A7366CBCE251034E4C873D1930B5AF029CA4F9CB48D752223A7F3F02732D93A20CF6203167431F827DC671F4FA14B26B4EC5C2AEBEE8BE55FB9CF4703AB60F701"),
            )
        }

        @JvmStatic
        fun jsonBlockProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    """{"type":"SEND","version":0,"algorithm":"V1","publicKey":"A5E7E4B3B93150314E1177D5B9DE0057626B16A4B3C3F1DB37DF67628A5EF457","height":2,"balance":1,"timestamp":"2024-01-07T08:26:49.211Z","previous":"6CC2D3A7513723B1BA59DE784BA546BAF6447464D0BA3D80004752D6F9F4BA23","receiverAlgorithm":"V1","receiverPublicKey":"552254E101B51B22080D084C12C94BF7DFC5BE0D973025D62C0BC1FF4D9B145F","amount":1}"""
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
                Arguments.of(sendBlock.copy(receiverAlgorithm = AttoAlgorithm.UNKNOWN)), // unknown receiver algorithm
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
    receiverAlgorithm = AttoAlgorithm.V1,
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