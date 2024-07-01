@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
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
    fun `should serialize and deserialize json`(expectedBlock: AttoBlock) {
        // when
        val json = Json.encodeToString(AttoBlock.serializer(), expectedBlock)
        val block = Json.decodeFromString<AttoBlock>(json)

        // then
        assertEquals(expectedBlock, block)
    }

    @ParameterizedTest
    @MethodSource("jsonBlockProvider")
    fun `should deserialize json`(expectedJson: String) {
        // when
        val block = Json.decodeFromString<AttoBlock>(expectedJson)
        val json = Json.encodeToString(AttoBlock.serializer(), block)

        // then
        assertEquals(expectedJson.compactJson(), json)
    }

    @ParameterizedTest
    @MethodSource("blockProvider")
    fun `should serialize and deserialize bytes`(expectedBlock: AttoBlock) {
        // when
        val bytes = expectedBlock.toBuffer()
        val block = AttoBlock.fromBuffer(bytes)

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
                    "0A0D4174746F53656E64426C6F636B1279000008001220EF8ECF7CDA3C2B8E15D3BFA65C7F35C01B11E8910259ABF3B296C830E33FC08C1802200128C5DCE6AA86323220A9545DFBDADDE2228A0D672E64058BF95FC27F2F9AB177F7E49B31795A1A739138004220C3FB0AC110D859DA2CE1F13743B3B37315FD0EF593CDBCE4FDA53A7B8612B3A04801",
                ),
                // RECEIVE
                Arguments.of(
                    "0A104174746F52656365697665426C6F636B12800100000800122025C85B55B4301266CBEC2ABE53D6055FA80809A8B37B5720E16678EB813BE5001802208080A0A89C94B6E6F90128C6DCE6AA86323220F2A495BDF4EF3E0B3568EA4A4843C3F686D05847A8E577F94BCCE6158953D23038004220830982410A9A3B9B9E97975C7B304924958C6109C02953C62ABA3A80D8256530",
                ),
                // OPEN
                Arguments.of(
                    "0A0D4174746F4F70656E426C6F636B127E0000080012208C7C7084202251F4EC492E59C6CC4A100C7BC1C9A6BFB077F44E3270C4E47E26188080A0A89C94B6E6F90120E4DBE6AA863228003220270649CF310BC8C83CF4EB44691B76332E8D34C5CCC4F5769BF3E6F8DCB522393A209F53151A064E81099422E8E8E97AB4CF7F6E7FCA2D1C242EE87D60E0004BB108",
                ),
                // CHANGE
                Arguments.of(
                    "0A0F4174746F4368616E6765426C6F636B127E00000800122024EEE316489C49D26BC9793E710DC2DC25501EB7510236806D65918F0BCA82DB1802208080A0A89C94B6E6F90128C6DCE6AA8632322088343CDFED7A12E4F06ECFF62EB65C6DDEF5365C5B9390D44C731E6168F578FD3A20A697D8686EE802321137F34755B32F08B240EAED681D3A35D5BAFE07547FC43A",
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
                       "amount":1
                    }
                    """,
                ),
                Arguments.of(
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
                       "sendHash":"EE5FDA9A1ACEC7A09231792C345CDF5CD29F1059E5C413535D9FCA66A1FB2F49"
                    }
                    """,
                ),
                Arguments.of(
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
                       "representative":"69C010A8A74924D083D1FC8234861B4B357530F42341484B4EBDA6B99F047105"
                    }
                    """,
                ),
                Arguments.of(
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
                       "representative":"020552FC788288CCDC239AE862A75A6B9BA62883EC099B7D6E5DFAA97C044BBB"
                    }
                    """,
                ),
            )

        @JvmStatic
        fun invalidBlockProvider(): Stream<Arguments> =
            Stream.of(
                // unknown version
                Arguments.of(sendBlock.copy(version = UShort.MAX_VALUE.toAttoVersion())),
                // future timestamp
                Arguments.of(receiveBlock.copy(timestamp = Clock.System.now().plus(1.days))),
                // invalid height
                Arguments.of(sendBlock.copy(height = 0U.toAttoHeight())),
                // invalid height
                Arguments.of(receiveBlock.copy(height = 0U.toAttoHeight())),
                // invalid height
                Arguments.of(changeBlock.copy(height = 0U.toAttoHeight())),
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
        version = 0U.toAttoVersion(),
        network = AttoNetwork.LOCAL,
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
        version = 0U.toAttoVersion(),
        network = AttoNetwork.LOCAL,
        algorithm = AttoAlgorithm.V1,
        publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
        height = 2U.toAttoHeight(),
        balance = AttoAmount(1U),
        timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
        previous = AttoHash(Random.Default.nextBytes(ByteArray(32))),
        receiverAlgorithm = AttoAlgorithm.V1,
        receiverPublicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
        amount = AttoAmount(1U),
    )

val receiveBlock =
    AttoReceiveBlock(
        version = 0U.toAttoVersion(),
        network = AttoNetwork.LOCAL,
        algorithm = AttoAlgorithm.V1,
        publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
        height = 2U.toAttoHeight(),
        balance = AttoAmount.MAX,
        timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
        previous = AttoHash(Random.nextBytes(ByteArray(32))),
        sendHashAlgorithm = AttoAlgorithm.V1,
        sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32))),
    )

val changeBlock =
    AttoChangeBlock(
        version = 0U.toAttoVersion(),
        network = AttoNetwork.LOCAL,
        algorithm = AttoAlgorithm.V1,
        publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
        height = 2U.toAttoHeight(),
        balance = AttoAmount.MAX,
        timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
        previous = AttoHash(Random.nextBytes(ByteArray(32))),
        representative = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    )
