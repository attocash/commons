package cash.atto.commons

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountUpdateTest {
    private val publicKey = AttoPublicKey(ByteArray(32) { it.toByte() })
    private val otherPublicKey = AttoPublicKey(ByteArray(32) { (it + 32).toByte() })
    private val representativeAddress = AttoAddress(AttoAlgorithm.V1, otherPublicKey)
    private val timestamp = AttoInstant.fromEpochMilliseconds(1_705_517_157_478)
    private val account =
        AttoAccount(
            network = AttoNetwork.LOCAL,
            version = 0.toAttoVersion(),
            address = AttoAddress(AttoAlgorithm.V1, publicKey),
            height = 2.toAttoHeight(),
            balance = 1_000.toAttoAmount(),
            lastTransactionHash = AttoHash(ByteArray(32) { (it + 64).toByte() }),
            lastTransactionTimestamp = timestamp,
            representativeAddress = AttoAddress(AttoAlgorithm.V1, otherPublicKey),
        )
    private val receivable =
        AttoReceivable(
            network = AttoNetwork.LOCAL,
            hash = AttoHash(ByteArray(32) { (it + 96).toByte() }),
            version = 0.toAttoVersion(),
            address = AttoAddress(AttoAlgorithm.V1, otherPublicKey),
            timestamp = AttoInstant.fromEpochMilliseconds(timestamp.toEpochMilliseconds() - 1),
            receiverAddress = AttoAddress(AttoAlgorithm.V1, publicKey),
            amount = 100.toAttoAmount(),
        )

    @Test
    fun `should delegate account updates to domain operations`() {
        // Given
        val timestampString = timestamp.toString()

        // When
        val expectedOpen = AttoAccount.open(representativeAddress, receivable, timestamp)
        val expectedSend = account.send(representativeAddress, 100.toAttoAmount(), timestamp)
        val expectedReceive = account.receive(receivable, timestamp)
        val expectedChange = account.change(representativeAddress, timestamp)

        // Then
        assertUpdate(expectedOpen, attoAccountOpen(representativeAddress, receivable, timestampString))
        assertUpdate(expectedSend, attoAccountSend(account, representativeAddress, 100.toAttoAmount(), timestampString))
        assertUpdate(expectedReceive, attoAccountReceive(account, receivable, timestampString))
        assertUpdate(expectedChange, attoAccountChange(account, representativeAddress, timestampString))
    }

    @Test
    fun `should expose canonical work targets`() {
        val openBlock = attoAccountOpen(representativeAddress, receivable, timestamp.toString()).block
        val sendBlock = attoAccountSend(account, representativeAddress, 100.toAttoAmount(), timestamp.toString()).block

        assertEquals(openBlock.publicKey.toString(), attoBlockWorkTarget(openBlock))
        assertEquals(account.lastTransactionHash.toString(), attoBlockWorkTarget(sendBlock))
    }

    private fun assertUpdate(
        expected: Pair<AttoBlock, AttoAccount>,
        actual: AccountUpdate,
    ) {
        assertEquals(expected.first, actual.block)
        assertEquals(expected.second, actual.account)
    }
}
