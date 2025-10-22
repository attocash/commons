package cash.atto.commons

import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class AttoAccountTest {
    @Test
    fun `should open account`() {
        // given
        val receivable =
            AttoReceivable(
                network = AttoNetwork.LOCAL,
                version = 0U.toAttoVersion(),
                algorithm = AttoAlgorithm.V1,
                publicKey = AttoPublicKey(Random.Default.nextBytes(32)),
                receiverAlgorithm = AttoAlgorithm.V1,
                receiverPublicKey = AttoPublicKey(Random.Default.nextBytes(32)),
                amount = 1000UL.toAttoAmount(),
                timestamp = AttoInstant.now() - 10.seconds,
                hash = AttoHash(Random.Default.nextBytes(32)),
            )

        val representativeAlgorithm = AttoAlgorithm.V1
        val representativePublicKey = AttoPublicKey(Random.Default.nextBytes(32))
        val timestamp = AttoInstant.now()

        // when
        val (openBlock, newAccount) =
            AttoAccount.open(
                representativeAlgorithm = representativeAlgorithm,
                representativePublicKey = representativePublicKey,
                receivable = receivable,
                timestamp = timestamp,
            )

        // then
        assertEquals(receivable.receiverPublicKey, newAccount.publicKey)
        assertEquals(1U.toAttoHeight(), newAccount.height)
        assertEquals(receivable.amount, newAccount.balance)
        assertEquals(openBlock.hash, newAccount.lastTransactionHash)
        assertEquals(timestamp, newAccount.lastTransactionTimestamp)
        assertEquals(representativeAlgorithm, newAccount.representativeAlgorithm)
        assertEquals(representativePublicKey, newAccount.representativePublicKey)
    }

    @Test
    fun `should send amount`() {
        // given
        val account = AttoAccount.sample()
        val receiverPublicKey = AttoPublicKey(Random.Default.nextBytes(32))
        val receiverAlgorithm = AttoAlgorithm.V1
        val amount = 100UL.toAttoAmount()
        val timestamp = account.lastTransactionTimestamp.plus(1.seconds)

        // when
        val (sendBlock, updatedAccount) =
            account.send(
                receiverAlgorithm = receiverAlgorithm,
                receiverPublicKey = receiverPublicKey,
                amount = amount,
                timestamp = timestamp,
            )

        // then
        assertEquals(account.balance - amount, updatedAccount.balance)
        assertEquals(account.height + 1U, updatedAccount.height)
        assertEquals(sendBlock.hash, updatedAccount.lastTransactionHash)
        assertEquals(timestamp, updatedAccount.lastTransactionTimestamp)
    }

    @Test
    fun `should receive amount`() {
        // given
        val account = AttoAccount.sample()
        val receivable =
            AttoReceivable(
                network = AttoNetwork.LOCAL,
                hash = AttoHash(Random.Default.nextBytes(32)),
                version = 0U.toAttoVersion(),
                algorithm = AttoAlgorithm.V1,
                publicKey = AttoPublicKey(Random.Default.nextBytes(32)),
                timestamp = account.lastTransactionTimestamp.minus(5.seconds),
                receiverAlgorithm = account.algorithm,
                receiverPublicKey = account.publicKey,
                amount = 200UL.toAttoAmount(),
            )
        val timestamp = account.lastTransactionTimestamp.plus(1.seconds)

        // when
        val (receiveBlock, updatedAccount) =
            account.receive(
                receivable = receivable,
                timestamp = timestamp,
            )

        // then
        assertEquals(account.balance + receivable.amount, updatedAccount.balance)
        assertEquals(account.height + 1U, updatedAccount.height)
        assertEquals(receiveBlock.hash, updatedAccount.lastTransactionHash)
        assertEquals(timestamp, updatedAccount.lastTransactionTimestamp)
    }

    @Test
    fun `should not receive with timestamp before receivable timestamp`() {
        // given
        val account = AttoAccount.sample()
        val receivableTimestamp = account.lastTransactionTimestamp.plus(5.seconds)
        val receivable =
            AttoReceivable(
                network = AttoNetwork.LOCAL,
                hash = AttoHash(Random.Default.nextBytes(32)),
                version = 0U.toAttoVersion(),
                algorithm = account.algorithm,
                publicKey = account.publicKey,
                timestamp = receivableTimestamp,
                receiverAlgorithm = account.algorithm,
                receiverPublicKey = account.publicKey,
                amount = 200UL.toAttoAmount(),
            )
        val timestamp = account.lastTransactionTimestamp.plus(1.seconds)

        // when
        val exception =
            assertFailsWith<IllegalArgumentException> {
                account.receive(
                    receivable = receivable,
                    timestamp = timestamp,
                )
            }

        // then
        assertEquals("Timestamp can't be before receivable timestamp", exception.message)
    }

    @Test
    fun `should change representative`() {
        // given
        val account = AttoAccount.sample()
        val newRepresentativeAlgorithm = AttoAlgorithm.V1
        val newRepresentativePublicKey = AttoPublicKey(Random.Default.nextBytes(32))
        val timestamp = account.lastTransactionTimestamp.plus(1.seconds)

        // when
        val (changeBlock, updatedAccount) =
            account.change(
                representativeAlgorithm = newRepresentativeAlgorithm,
                representativePublicKey = newRepresentativePublicKey,
                timestamp = timestamp,
            )

        // then
        assertEquals(account.balance, updatedAccount.balance)
        assertEquals(account.height + 1U, updatedAccount.height)
        assertEquals(changeBlock.hash, updatedAccount.lastTransactionHash)
        assertEquals(timestamp, updatedAccount.lastTransactionTimestamp)
        assertEquals(newRepresentativeAlgorithm, updatedAccount.representativeAlgorithm)
        assertEquals(newRepresentativePublicKey, updatedAccount.representativePublicKey)
    }

    @Test
    fun `should not send to self`() {
        // given
        val account = AttoAccount.sample()
        val timestamp = account.lastTransactionTimestamp.plus(1.seconds)

        // when
        val exception =
            assertFailsWith<IllegalArgumentException> {
                account.send(
                    receiverAlgorithm = account.algorithm,
                    receiverPublicKey = account.publicKey,
                    amount = 100UL.toAttoAmount(),
                    timestamp = timestamp,
                )
            }

        // then
        assertEquals("You can't send money to yourself", exception.message)
    }

    @Test
    fun `should serialize json`() {
        // given
        val expectedAccount =
            AttoAccount(
                publicKey = AttoPublicKey(Random.Default.nextBytes(32)),
                network = AttoNetwork.LOCAL,
                version = 0U.toAttoVersion(),
                algorithm = AttoAlgorithm.V1,
                height = 1U.toAttoHeight(),
                balance = AttoAmount.MAX,
                lastTransactionHash = AttoHash(Random.Default.nextBytes(32)),
                lastTransactionTimestamp = AttoInstant.now(),
                representativeAlgorithm = AttoAlgorithm.V1,
                representativePublicKey = AttoPublicKey(Random.Default.nextBytes(32)),
            )

        // when
        val json = Json.encodeToString(expectedAccount)
        val account = Json.decodeFromString<AttoAccount>(json)

        // then
        assertEquals(expectedAccount, account)
    }

    @Test
    fun `should deserialize json`() {
        // given
        val expectedJson =
            """
                {
                   "publicKey":"45B3B58C26181580EEAFC1791046D54EEC2854BF550A211E2362761077D6590C",
                   "network":"LOCAL",
                   "version":0,
                   "algorithm":"V1",
                   "height":1,
                   "balance":18000000000000000000,
                   "lastTransactionHash":"70F9406609BCB2E3E18F22BD0839C95E5540E95489DC6F24DBF6A1F7CFD83A92",
                   "lastTransactionTimestamp":1705517157478,
                   "representativeAlgorithm":"V1",
                   "representativePublicKey":"99E439410A4DDD2A3A8D0B667C7A090286B8553378CF3C7AA806C3E60B6C4CBE"
                }
          """

        // when
        val account = Json.decodeFromString<AttoAccount>(expectedJson)
        val json = Json.encodeToString(account)

        // then
        assertEquals(expectedJson.compactJson(), json)
    }

    private fun AttoAccount.Companion.sample(): AttoAccount =
        AttoAccount(
            publicKey = AttoPublicKey(Random.Default.nextBytes(32)),
            network = AttoNetwork.LOCAL,
            version = 0U.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            height = 1U.toAttoHeight(),
            balance = AttoAmount(ULong.MAX_VALUE / 2U),
            lastTransactionHash = AttoHash(Random.Default.nextBytes(32)),
            lastTransactionTimestamp = AttoInstant.now(),
            representativeAlgorithm = AttoAlgorithm.V1,
            representativePublicKey = AttoPublicKey(Random.Default.nextBytes(32)),
        )
}
