package cash.atto.commons

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

class AttoJsonTest {
    private val publicKey = AttoPublicKey(ByteArray(32) { it.toByte() })
    private val otherPublicKey = AttoPublicKey(ByteArray(32) { (it + 32).toByte() })
    private val hash = AttoHash(ByteArray(32) { (it + 64).toByte() })
    private val timestamp = AttoInstant.fromEpochMilliseconds(1_705_517_157_478)
    private val account =
        AttoAccount(
            publicKey = publicKey,
            network = AttoNetwork.LOCAL,
            version = 0.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            height = 2.toAttoHeight(),
            balance = 1_000.toAttoAmount(),
            lastTransactionHash = hash,
            lastTransactionTimestamp = timestamp,
            representativeAlgorithm = AttoAlgorithm.V1,
            representativePublicKey = otherPublicKey,
        )

    @Test
    fun `should preserve maximum account numeric fields`() {
        // Given
        val maximumAccount =
            account.copy(
                version = UShort.MAX_VALUE.toAttoVersion(),
                height = AttoHeight.MAX,
                balance = AttoAmount.MAX,
                lastTransactionTimestamp = AttoInstant.fromEpochMilliseconds(Long.MAX_VALUE),
            )

        // When
        val json = maximumAccount.toJson()

        // Then
        assertContains(json, "\"version\":65535")
        assertContains(json, "\"height\":\"18446744073709551615\"")
        assertContains(json, "\"balance\":\"18000000000000000000\"")
        assertContains(json, "\"lastTransactionTimestamp\":\"9223372036854775807\"")
        assertEquals(maximumAccount, AttoAccount.fromJson(json))
    }

    @Test
    fun `should preserve maximum send transaction fields`() {
        // Given
        val maximumPublicKey = AttoPublicKey(ByteArray(32) { -1 })
        val block =
            AttoSendBlock(
                network = AttoNetwork.LOCAL,
                version = UShort.MAX_VALUE.toAttoVersion(),
                algorithm = AttoAlgorithm.V1,
                publicKey = maximumPublicKey,
                height = AttoHeight.MAX,
                balance = AttoAmount.MAX,
                timestamp = AttoInstant.fromEpochMilliseconds(Long.MAX_VALUE),
                previous = AttoHash(ByteArray(32) { -1 }),
                receiverAlgorithm = AttoAlgorithm.V1,
                receiverPublicKey = maximumPublicKey,
                amount = AttoAmount.MAX,
            )
        val transaction =
            AttoTransaction(
                block = block,
                signature = AttoSignature(ByteArray(AttoSignature.SIZE) { -1 }),
                work = AttoWork(ByteArray(AttoWork.SIZE) { -1 }),
            )

        // When
        val json = transaction.toJson()
        val fromJson = AttoTransaction.fromJson(json)
        val bytes = transaction.toByteArray()
        val fromBytes = AttoTransaction.fromByteArray(bytes)

        // Then
        assertContains(json, "\"version\":65535")
        assertContains(json, "\"height\":\"18446744073709551615\"")
        assertContains(json, "\"balance\":\"18000000000000000000\"")
        assertContains(json, "\"timestamp\":\"9223372036854775807\"")
        assertContains(json, "\"amount\":\"18000000000000000000\"")
        assertEquals(transaction, fromJson)
        assertEquals(transaction, fromBytes)
        assertContentEquals(bytes, fromJson.toByteArray())
        assertEquals(transaction.hash, fromJson.hash)
        assertEquals(transaction.hash, fromBytes.hash)
    }

    @Test
    fun `should accept legacy and mixed numeric account fields`() {
        // Given
        val expected =
            account.copy(
                height = AttoHeight.MAX,
                balance = AttoAmount(AttoAmount.MAX.raw - 1UL),
                lastTransactionTimestamp = AttoInstant.fromEpochMilliseconds(Long.MAX_VALUE),
            )
        val legacyJson =
            """
            {
                "publicKey":"$publicKey",
                "network":"LOCAL",
                "version":0,
                "algorithm":"V1",
                "height":18446744073709551615,
                "balance":17999999999999999999,
                "lastTransactionHash":"$hash",
                "lastTransactionTimestamp":9223372036854775807,
                "representativeAlgorithm":"V1",
                "representativePublicKey":"$otherPublicKey"
            }
            """.trimIndent()
        val mixedJson = legacyJson.replace("\"height\":18446744073709551615", "\"height\":\"18446744073709551615\"")

        // When
        val legacyAccount = AttoAccount.fromJson(legacyJson)
        val mixedAccount = AttoAccount.fromJson(mixedJson)

        // Then
        assertEquals(expected, legacyAccount)
        assertEquals(expected, mixedAccount)
        assertEquals(expected, AttoAccount.fromJson(legacyAccount.toJson()))
    }

    @Test
    fun `should ignore unknown fields and reject malformed json`() {
        // Given
        val jsonWithUnknownField = account.toJson().dropLast(1) + ",\"unknown\":true}"

        // When
        val decodedAccount = AttoAccount.fromJson(jsonWithUnknownField)

        // Then
        assertEquals(account, decodedAccount)
        assertFails { AttoAccount.fromJson("{") }
    }
}
