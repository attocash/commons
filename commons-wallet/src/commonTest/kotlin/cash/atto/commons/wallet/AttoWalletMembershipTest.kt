package cash.atto.commons.wallet

import cash.atto.commons.AttoKeyIndex
import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.toAttoIndex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttoWalletMembershipTest {
    @Test
    fun `opening another index for an existing address preserves its membership`() =
        runTest {
            // Given
            val signer = WalletTestData.privateKey.toSigner()
            val wallet = AttoWallet(WalletTestNodeClient(), WalletTestWorker()) { signer }
            val first = 0U.toAttoIndex()
            val second = 1U.toAttoIndex()
            wallet.openAccount(first)

            // When
            assertFailsWith<IllegalArgumentException> { wallet.openAccount(second) }
            wallet.closeAccount(second)

            // Then
            assertTrue(wallet.isOpen(first))
            assertFalse(wallet.isOpen(second))
            assertTrue(wallet.isOpen(signer.address))
            assertEquals(signer.address, wallet.getAddress(first))
            assertEquals(setOf(signer.address), wallet.addressFlow().first())
        }

    @Test
    fun `opening a batch with duplicate addresses rejects the entire batch`() =
        runTest {
            // Given
            val signer = WalletTestData.privateKey.toSigner()
            val wallet = AttoWallet(WalletTestNodeClient(), WalletTestWorker()) { signer }
            val first = 0U.toAttoIndex()
            val second = 1U.toAttoIndex()

            // When
            assertFailsWith<IllegalArgumentException> { wallet.openAccount(listOf(first, second)) }

            // Then
            assertFalse(wallet.isOpen(first))
            assertFalse(wallet.isOpen(second))
            assertFalse(wallet.isOpen(signer.address))
            assertEquals(emptySet(), wallet.addressFlow().first())
        }

    @Test
    fun `duplicate address rejection leaves other candidates unopened`() =
        runTest {
            // Given
            val existingSigner = WalletTestData.privateKey.toSigner()
            val otherSigner = AttoPrivateKey(ByteArray(32) { 2 }).toSigner()
            val first = 0U.toAttoIndex()
            val second = 1U.toAttoIndex()
            val third = 2U.toAttoIndex()
            val wallet =
                AttoWallet(WalletTestNodeClient(), WalletTestWorker()) { index: AttoKeyIndex ->
                    if (index == second) otherSigner else existingSigner
                }
            wallet.openAccount(first)

            // When
            assertFailsWith<IllegalArgumentException> { wallet.openAccount(listOf(second, third)) }

            // Then
            assertTrue(wallet.isOpen(first))
            assertFalse(wallet.isOpen(second))
            assertFalse(wallet.isOpen(third))
            assertFalse(wallet.isOpen(otherSigner.address))
            assertEquals(setOf(existingSigner.address), wallet.addressFlow().first())
        }

    @Test
    fun `repeated indexes in one request open one account and allow reopening after close`() =
        runTest {
            // Given
            val signer = WalletTestData.privateKey.toSigner()
            val wallet = AttoWallet(WalletTestNodeClient(), WalletTestWorker()) { signer }
            val index = 0U.toAttoIndex()

            // When
            val opened = wallet.openAccount(listOf(index, index))
            wallet.closeAccount(index)
            val reopened = wallet.openAccount(index)

            // Then
            assertEquals(listOf(index), opened.map { it.index })
            assertEquals(index, reopened.index)
            assertTrue(wallet.isOpen(index))
            assertTrue(wallet.isOpen(signer.address))
            assertEquals(setOf(signer.address), wallet.addressFlow().first())
        }

    @Test
    fun `competing opens for one address leave exactly one index registered`() =
        runTest {
            // Given
            val signer = WalletTestData.privateKey.toSigner()
            val providerStarted = CompletableDeferred<Unit>()
            val releaseProvider = CompletableDeferred<Unit>()
            var providerCalls = 0
            val wallet =
                AttoWallet(WalletTestNodeClient(), WalletTestWorker()) {
                    providerCalls++
                    providerStarted.complete(Unit)
                    releaseProvider.await()
                    signer
                }
            val indexes = listOf(0U.toAttoIndex(), 1U.toAttoIndex())

            // When
            val first = async { runCatching { wallet.openAccount(indexes[0]) } }
            providerStarted.await()
            val second = async { runCatching { wallet.openAccount(indexes[1]) } }
            yield()
            val providerCallsWhileOpening = providerCalls
            releaseProvider.complete(Unit)
            val results = awaitAll(first, second)

            // Then
            assertEquals(1, providerCallsWhileOpening)
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, results.count { it.exceptionOrNull() is IllegalArgumentException })
            assertEquals(1, indexes.count { wallet.isOpen(it) })
            assertTrue(wallet.isOpen(signer.address))
            assertEquals(setOf(signer.address), wallet.addressFlow().first())
        }
}
