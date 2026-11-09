package cash.atto.commons.wallet

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.PreviousSupport
import cash.atto.commons.toAttoIndex
import cash.atto.commons.toAttoVersion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AttoWalletTransitionTest {
    @Test
    fun `same account transitions publish in order and commit only after publication`() =
        runTest {
            // Given
            val signer = WalletTestData.privateKey.toSigner()
            val original = WalletTestData.account(signer.address)
            val published = mutableListOf<AttoTransaction>()
            val releaseFirstPublish = CompletableDeferred<Unit>()
            val firstPublishStarted = CompletableDeferred<Unit>()
            val client =
                object : WalletTestNodeClient(listOf(original)) {
                    override suspend fun publish(transaction: AttoTransaction) {
                        published.add(transaction)
                        if (published.size == 1) {
                            firstPublishStarted.complete(Unit)
                            releaseFirstPublish.await()
                        }
                    }
                }
            val wallet = AttoWallet(client, WalletTestWorker()) { signer }
            val index = 0U.toAttoIndex()
            wallet.openAccount(index)

            // When
            val send = async { wallet.send(index, WalletTestData.foreignAddress, AttoAmount(1U)) }
            firstPublishStarted.await()
            val change = async { wallet.change(index, signer.address) }
            runCurrent()

            // Then: the second operation waits and unacknowledged publication leaves cached state untouched.
            assertEquals(1, published.size)
            assertEquals(original, wallet.getAccount(index))
            assertFalse(change.isCompleted)
            releaseFirstPublish.complete(Unit)
            val sent = send.await()
            val changed = change.await()
            assertEquals(listOf(sent, changed), published)
            assertEquals(sent.hash, (changed.block as PreviousSupport).previous)
            assertEquals(original.height + 2U, wallet.getAccount(index)?.height)
            assertEquals(original.balance - AttoAmount(1U), wallet.getAccount(index)?.balance)
            assertEquals(signer.address, wallet.getAccount(index)?.representativeAddress)
        }

    @Test
    fun `different wallet accounts transition independently`() =
        runTest {
            // Given
            val firstSigner = WalletTestData.privateKey.toSigner()
            val secondSigner = AttoPrivateKey(ByteArray(32) { 2 }).toSigner()
            val firstAccount = WalletTestData.account(firstSigner.address)
            val secondAccount = WalletTestData.account(secondSigner.address)
            val firstIndex = 0U.toAttoIndex()
            val secondIndex = 1U.toAttoIndex()
            val releaseFirstPublish = CompletableDeferred<Unit>()
            val firstPublishStarted = CompletableDeferred<Unit>()
            val entered = mutableListOf<AttoAddress>()
            val client =
                object : WalletTestNodeClient(listOf(firstAccount, secondAccount)) {
                    override suspend fun publish(transaction: AttoTransaction) {
                        entered.add(transaction.address)
                        if (transaction.address == firstSigner.address) {
                            firstPublishStarted.complete(Unit)
                            releaseFirstPublish.await()
                        }
                    }
                }
            val wallet = AttoWallet(client, WalletTestWorker()) { if (it == firstIndex) firstSigner else secondSigner }
            wallet.openAccount(listOf(firstIndex, secondIndex))

            // When
            val first = async { wallet.send(firstIndex, WalletTestData.foreignAddress, AttoAmount(1U)) }
            firstPublishStarted.await()
            val second = async { wallet.change(secondIndex, secondSigner.address) }
            second.await()

            // Then
            assertEquals(listOf(firstSigner.address, secondSigner.address), entered)
            assertFalse(first.isCompleted)
            assertTrue(second.isCompleted)
            assertEquals(firstAccount, wallet.getAccount(firstIndex))
            assertEquals(secondAccount.height + 1U, wallet.getAccount(secondIndex)?.height)
            releaseFirstPublish.complete(Unit)
            first.await()
            second.await()
        }

    @Test
    fun `failed publication retains cached state for send receive change and first receive`() =
        runTest {
            // Given
            val signer = WalletTestData.privateKey.toSigner()
            val original = WalletTestData.account(signer.address)
            val failure = IllegalStateException("Node rejected publication")
            var attempts = 0

            fun client(accounts: List<AttoAccount>) =
                object : WalletTestNodeClient(accounts) {
                    override suspend fun publish(transaction: AttoTransaction) {
                        attempts++
                        throw failure
                    }
                }
            val wallet = AttoWallet(client(listOf(original)), WalletTestWorker()) { signer }
            val unopened = AttoWallet(client(emptyList()), WalletTestWorker()) { signer }
            val index = 0U.toAttoIndex()
            wallet.openAccount(index)
            unopened.openAccount(index)
            val receivable = receivable(signer.address)
            val timestamp = WalletTestData.timestamp + 1.seconds

            // When
            val sendFailure =
                assertFailsWith<IllegalStateException> {
                    wallet.send(index, WalletTestData.foreignAddress, AttoAmount(1U), timestamp)
                }
            val receiveFailure =
                assertFailsWith<IllegalStateException> {
                    wallet.receive(receivable, timestamp = timestamp)
                }
            val changeFailure =
                assertFailsWith<IllegalStateException> {
                    wallet.change(index, signer.address, timestamp)
                }
            val openFailure =
                assertFailsWith<IllegalStateException> {
                    unopened.receive(receivable, WalletTestData.foreignAddress, timestamp)
                }

            // Then
            assertEquals(4, attempts)
            listOf(sendFailure, receiveFailure, changeFailure, openFailure).forEach { assertSame(failure, it) }
            assertEquals(original, wallet.getAccount(index))
            assertNull(unopened.getAccount(index))
        }

    @Test
    fun `cancelling publication releases the account without advancing its state`() =
        runTest {
            // Given
            val signer = WalletTestData.privateKey.toSigner()
            val original = WalletTestData.account(signer.address)
            val publicationStarted = CompletableDeferred<Unit>()
            val publicationFinished = CompletableDeferred<Unit>()
            val client =
                object : WalletTestNodeClient(listOf(original)) {
                    override suspend fun publish(transaction: AttoTransaction) {
                        publicationStarted.complete(Unit)
                        publicationFinished.await()
                    }
                }
            val wallet = AttoWallet(client, WalletTestWorker()) { signer }
            val index = 0U.toAttoIndex()
            wallet.openAccount(index)
            val pending = async { wallet.send(index, WalletTestData.foreignAddress, AttoAmount(1U)) }
            publicationStarted.await()

            // When
            pending.cancelAndJoin()
            publicationFinished.complete(Unit)
            val changed = wallet.change(index, signer.address)

            // Then
            assertEquals(original.lastTransactionHash, (changed.block as PreviousSupport).previous)
            assertEquals(original.height + 1U, wallet.getAccount(index)?.height)
            assertEquals(original.balance, wallet.getAccount(index)?.balance)
        }

    private fun receivable(address: AttoAddress): AttoReceivable =
        AttoReceivable(
            network = AttoNetwork.LOCAL,
            hash = AttoHash(ByteArray(32) { 4 }),
            version = 0U.toAttoVersion(),
            address = WalletTestData.foreignAddress,
            timestamp = WalletTestData.timestamp,
            receiverAddress = address,
            amount = AttoAmount(1U),
        )
}
