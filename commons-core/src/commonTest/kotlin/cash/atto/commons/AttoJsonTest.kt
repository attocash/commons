package cash.atto.commons

import kotlin.test.Test
import kotlin.test.assertContains
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
    fun `should preserve maximum unsigned integers`() {
        // Given
        val maximumAccount = account.copy(height = AttoHeight.MAX, balance = AttoAmount.MAX)

        // When
        val json = maximumAccount.toJson()

        // Then
        assertContains(json, AttoHeight.MAX.toString())
        assertContains(json, AttoAmount.MAX.toString())
        assertEquals(maximumAccount, AttoAccount.fromJson(json))
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
