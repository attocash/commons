package cash.atto.commons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class AttoAccountTest {
    @Test
    fun `should open account`() {
        // Given
        val receivable =
            AttoReceivable(
                network = AttoNetwork.LOCAL,
                version = 0U.toAttoVersion(),
                address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.Default.nextBytes(32))),
                receiverAddress = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.Default.nextBytes(32))),
                amount = 1000UL.toAttoAmount(),
                timestamp = AttoInstant.now() - 10.seconds,
                hash = AttoHash(Random.Default.nextBytes(32)),
            )

        val representativeAddress = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.Default.nextBytes(32)))
        val timestamp = AttoInstant.now()

        // When
        val (openBlock, newAccount) =
            AttoAccount.open(
                representativeAddress = representativeAddress,
                receivable = receivable,
                timestamp = timestamp,
            )

        // Then
        assertEquals(receivable.receiverPublicKey, newAccount.publicKey)
        assertEquals(1U.toAttoHeight(), newAccount.height)
        assertEquals(receivable.amount, newAccount.balance)
        assertEquals(openBlock.hash, newAccount.lastTransactionHash)
        assertEquals(timestamp, newAccount.lastTransactionTimestamp)
        assertEquals(representativeAddress, openBlock.representativeAddress)
        assertEquals(representativeAddress, newAccount.representativeAddress)
    }

    @Test
    fun `should send amount`() {
        // Given
        val account = AttoAccount.sample()
        val receiverAddress = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.Default.nextBytes(32)))
        val amount = 100UL.toAttoAmount()
        val timestamp = account.lastTransactionTimestamp.plus(1.seconds)

        // When
        val (sendBlock, updatedAccount) =
            account.send(
                receiverAddress = receiverAddress,
                amount = amount,
                timestamp = timestamp,
            )

        // Then
        assertEquals(receiverAddress, sendBlock.receiverAddress)
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
                address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.Default.nextBytes(32))),
                timestamp = account.lastTransactionTimestamp.minus(5.seconds),
                receiverAddress = account.address,
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
                address = account.address,
                timestamp = receivableTimestamp,
                receiverAddress = account.address,
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
        // Given
        val account = AttoAccount.sample()
        val representativeAddress = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.Default.nextBytes(32)))
        val timestamp = account.lastTransactionTimestamp.plus(1.seconds)

        // When
        val (changeBlock, updatedAccount) =
            account.change(
                representativeAddress = representativeAddress,
                timestamp = timestamp,
            )

        // Then
        assertEquals(account.balance, updatedAccount.balance)
        assertEquals(account.height + 1U, updatedAccount.height)
        assertEquals(changeBlock.hash, updatedAccount.lastTransactionHash)
        assertEquals(timestamp, updatedAccount.lastTransactionTimestamp)
        assertEquals(representativeAddress, changeBlock.representativeAddress)
        assertEquals(representativeAddress, updatedAccount.representativeAddress)
    }

    @Test
    fun `should not send to self`() {
        // Given
        val account = AttoAccount.sample()
        val timestamp = account.lastTransactionTimestamp.plus(1.seconds)

        // When
        val exception =
            assertFailsWith<IllegalArgumentException> {
                account.send(
                    receiverAddress = account.address,
                    amount = 100UL.toAttoAmount(),
                    timestamp = timestamp,
                )
            }

        // Then
        assertEquals("You can't send money to yourself", exception.message)
    }

    @Test
    fun `should serialize json`() {
        // given
        val expectedAccount =
            AttoAccount(
                address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.Default.nextBytes(32))),
                network = AttoNetwork.LOCAL,
                version = 0U.toAttoVersion(),
                height = 1U.toAttoHeight(),
                balance = AttoAmount.MAX,
                lastTransactionHash = AttoHash(Random.Default.nextBytes(32)),
                lastTransactionTimestamp = AttoInstant.now(),
                representativeAddress = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.Default.nextBytes(32))),
            )

        // when
        val json = expectedAccount.toJson()
        val account = AttoAccount.fromJson(json)

        // then
        assertEquals(expectedAccount, account)
    }

    @Test
    fun `should deserialize json`() {
        // Given
        val expectedJson =
            """
                {
                   "publicKey":"45B3B58C26181580EEAFC1791046D54EEC2854BF550A211E2362761077D6590C",
                   "network":"LOCAL",
                   "version":0,
                   "algorithm":"V1",
                   "height":"1",
                   "balance":"18000000000000000000",
                   "lastTransactionHash":"70F9406609BCB2E3E18F22BD0839C95E5540E95489DC6F24DBF6A1F7CFD83A92",
                   "lastTransactionTimestamp":"1705517157478",
                   "representativeAlgorithm":"V1",
                   "representativePublicKey":"99E439410A4DDD2A3A8D0B667C7A090286B8553378CF3C7AA806C3E60B6C4CBE"
                }
          """

        // When
        val account = AttoAccount.fromJson(expectedJson)
        val json = account.toJson()

        // Then
        assertEquals(expectedJson.compactJson(), json)
    }

    @Test
    fun `should deserialize account from addresses alone`() {
        // Given
        val expected = AttoAccount.sample()
        val encoded = Json.parseToJsonElement(expected.toJson()).jsonObject
        val projections = setOf("algorithm", "publicKey", "representativeAlgorithm", "representativePublicKey")
        val addressesOnly =
            JsonObject(
                encoded - projections +
                    mapOf(
                        "address" to JsonPrimitive(expected.address.toString()),
                        "representativeAddress" to JsonPrimitive(expected.representativeAddress.toString()),
                    ),
            )

        // When
        val decoded = AttoAccount.fromJson(addressesOnly.toString())

        // Then
        assertEquals(expected, decoded)
        assertEquals(encoded, Json.parseToJsonElement(decoded.toJson()))
    }

    @Test
    fun `should ignore malformed account identity projections when addresses are present`() {
        // Given
        val expected = AttoAccount.sample()
        val encoded = Json.parseToJsonElement(expected.toJson()).jsonObject
        val projections = setOf("algorithm", "publicKey", "representativeAlgorithm", "representativePublicKey")
        val malformed =
            JsonObject(
                encoded + projections.associateWith { JsonObject(emptyMap()) } +
                    mapOf(
                        "address" to JsonPrimitive(expected.address.toString()),
                        "representativeAddress" to JsonPrimitive(expected.representativeAddress.toString()),
                    ),
            )

        // When
        val decoded = AttoAccount.fromJson(malformed.toString())

        // Then
        assertEquals(expected, decoded)
        assertEquals(encoded, Json.parseToJsonElement(decoded.toJson()))
    }

    @Test
    fun `should derive account identity projections from copied addresses`() {
        // Given
        val original = AttoAccount.sample()

        // When
        val copied = original.copy(address = original.representativeAddress, representativeAddress = original.address)

        // Then
        assertEquals(original.representativeAddress.algorithm, copied.algorithm)
        assertEquals(original.representativeAddress.publicKey, copied.publicKey)
        assertEquals(original.address.algorithm, copied.representativeAlgorithm)
        assertEquals(original.address.publicKey, copied.representativePublicKey)
        assertEquals(copied, AttoAccount.fromJson(copied.toJson()))
    }

    private fun AttoAccount.Companion.sample(): AttoAccount =
        AttoAccount(
            address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.Default.nextBytes(32))),
            network = AttoNetwork.LOCAL,
            version = 0U.toAttoVersion(),
            height = 1U.toAttoHeight(),
            balance = AttoAmount(ULong.MAX_VALUE / 2U),
            lastTransactionHash = AttoHash(Random.Default.nextBytes(32)),
            lastTransactionTimestamp = AttoInstant.now(),
            representativeAddress = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.Default.nextBytes(32))),
        )
}
