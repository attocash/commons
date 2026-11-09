package cash.atto.commons.wallet

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoReceivable
import cash.atto.commons.node.monitor.createAccountMonitor
import cash.atto.commons.toAttoIndex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AttoWalletAsyncLifecycleTest {
    @Test
    fun `async wallet close stops receiver and membership binding without closing injected worker`() =
        runTest {
            // Given
            val signer = WalletTestData.privateKey.toSigner()
            var subscriptions = 0
            var stopped = 0
            val client =
                object : WalletTestNodeClient() {
                    override fun receivableStream(
                        addresses: Collection<AttoAddress>,
                        minAmount: AttoAmount,
                    ): Flow<AttoReceivable> =
                        flow {
                            subscriptions++
                            try {
                                awaitCancellation()
                            } finally {
                                stopped++
                            }
                        }
                }
            val monitor = client.createAccountMonitor()
            val worker = WalletTestWorker()
            val configuration = AttoWalletAsyncConfiguration()
            configuration.signerProvider = { signer }
            configuration.enableAutoReceiver(monitor, AttoAmount(1U), 10.seconds) { WalletTestData.foreignAddress }
            val wrapper = configuration.build(client, worker, StandardTestDispatcher(testScheduler))
            val index = 0U.toAttoIndex()
            wrapper.wallet.openAccount(index)
            runCurrent()
            assertEquals(1, subscriptions)
            assertTrue(monitor.isMonitored(signer.address))

            // When
            wrapper.close()
            runCurrent()
            wrapper.wallet.closeAccount(index)
            runCurrent()

            // Then: the receiver is stopped and the cancelled binding no longer changes membership.
            assertEquals(1, stopped)
            assertEquals(1, subscriptions)
            assertTrue(monitor.isMonitored(signer.address))
            assertFalse(worker.closed)
        }
}
