package cash.atto.commons

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AttoJsonExportTest {
    @Test
    fun `should preserve maximum account values through JavaScript JSON parsing`() {
        // Given
        val account = account(ULong.MAX_VALUE, AttoAmount.MAX.raw, Long.MAX_VALUE)

        // When
        val json = account.toJson()
        val reparsed = javascriptParseAndStringify(json)
        val restored = AttoAccount.fromJson(reparsed)

        // Then
        assertEquals("string,string,string,number", javascriptFieldTypes(json))
        assertEquals("18446744073709551615", javascriptHeight(json))
        assertEquals("18000000000000000000", javascriptBalance(json))
        assertEquals("9223372036854775807", javascriptTimestamp(json))
        assertEquals(account, restored)
    }

    @Test
    fun `should preserve adjacent account values through JavaScript JSON parsing`() {
        // Given
        val account = account(ULong.MAX_VALUE - 1UL, AttoAmount.MAX.raw - 1UL, Long.MAX_VALUE - 1L)

        // When
        val json = account.toJson()
        val reparsed = javascriptParseAndStringify(json)
        val restored = AttoAccount.fromJson(reparsed)

        // Then
        assertEquals("string,string,string,number", javascriptFieldTypes(json))
        assertEquals("18446744073709551614", javascriptHeight(json))
        assertEquals("17999999999999999999", javascriptBalance(json))
        assertEquals("9223372036854775806", javascriptTimestamp(json))
        assertEquals(account, restored)
    }

    @Test
    fun `should preserve transaction bytes and hash through JavaScript JSON parsing`() {
        // Given
        val publicKey = AttoPublicKey(ByteArray(32) { it.toByte() })
        val block =
            AttoSendBlock(
                network = AttoNetwork.LOCAL,
                version = UShort.MAX_VALUE.toAttoVersion(),
                address = AttoAddress(AttoAlgorithm.V1, publicKey),
                height = AttoHeight.MAX,
                balance = AttoAmount.MAX,
                timestamp = AttoInstant.fromEpochMilliseconds(Long.MAX_VALUE),
                previous = AttoHash(ByteArray(32) { -1 }),
                receiverAddress = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32) { (it + 32).toByte() })),
                amount = AttoAmount(AttoAmount.MAX.raw - 1UL),
            )
        val transaction =
            AttoTransaction(
                block = block,
                signature = AttoSignature(ByteArray(AttoSignature.SIZE) { -1 }),
                work = AttoWork(ByteArray(AttoWork.SIZE) { -1 }),
            )
        val expectedBytes = transaction.toByteArray()
        val expectedHash = transaction.hash

        // When
        val json = transaction.toJson()
        val reparsed = javascriptParseAndStringify(json)
        val restored = AttoTransaction.fromJson(reparsed)

        // Then
        assertEquals(transaction, restored)
        assertContentEquals(expectedBytes, restored.toByteArray())
        assertEquals(expectedHash, restored.hash)
    }

    private fun account(
        height: ULong,
        balance: ULong,
        timestamp: Long,
    ): AttoAccount =
        AttoAccount(
            network = AttoNetwork.LOCAL,
            version = UShort.MAX_VALUE.toAttoVersion(),
            address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32))),
            height = AttoHeight(height),
            balance = AttoAmount(balance),
            lastTransactionHash = AttoHash(ByteArray(32)),
            lastTransactionTimestamp = AttoInstant.fromEpochMilliseconds(timestamp),
            representativeAddress = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32))),
        )

    private fun javascriptParseAndStringify(json: String): String = js("JSON.stringify(JSON.parse(json))")

    private fun javascriptFieldTypes(json: String): String =
        js(
            "[typeof JSON.parse(json).height, typeof JSON.parse(json).balance, " +
                "typeof JSON.parse(json).lastTransactionTimestamp, typeof JSON.parse(json).version].join(',')",
        )

    private fun javascriptHeight(json: String): String = js("JSON.parse(json).height")

    private fun javascriptBalance(json: String): String = js("JSON.parse(json).balance")

    private fun javascriptTimestamp(json: String): String = js("JSON.parse(json).lastTransactionTimestamp")
}
