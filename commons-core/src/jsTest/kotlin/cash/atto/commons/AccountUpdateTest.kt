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
            publicKey = publicKey,
            network = AttoNetwork.LOCAL,
            version = 0.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            height = 2.toAttoHeight(),
            balance = 1_000.toAttoAmount(),
            lastTransactionHash = AttoHash(ByteArray(32) { (it + 64).toByte() }),
            lastTransactionTimestamp = timestamp,
            representativeAlgorithm = AttoAlgorithm.V1,
            representativePublicKey = otherPublicKey,
        )
    private val receivable =
        AttoReceivable(
            network = AttoNetwork.LOCAL,
            hash = AttoHash(ByteArray(32) { (it + 96).toByte() }),
            version = 0.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            publicKey = otherPublicKey,
            timestamp = AttoInstant.fromEpochMilliseconds(timestamp.toEpochMilliseconds() - 1),
            receiverAlgorithm = AttoAlgorithm.V1,
            receiverPublicKey = publicKey,
            amount = 100.toAttoAmount(),
        )

    @Test
    fun `should delegate account updates to domain operations`() {
        val timestampString = timestamp.toString()

        val expectedOpen = AttoAccount.open(AttoAlgorithm.V1, otherPublicKey, receivable, timestamp)
        val expectedSend = account.send(AttoAlgorithm.V1, otherPublicKey, 100.toAttoAmount(), timestamp)
        val expectedReceive = account.receive(receivable, timestamp)
        val expectedChange = account.change(AttoAlgorithm.V1, otherPublicKey, timestamp)

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
