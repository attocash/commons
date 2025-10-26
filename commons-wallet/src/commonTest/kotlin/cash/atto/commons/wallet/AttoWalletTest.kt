package cash.atto.commons.wallet

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoMnemonic
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoUnit
import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.node.AttoNodeMock
import cash.atto.commons.node.AttoWorkerMock
import cash.atto.commons.node.create
import cash.atto.commons.node.monitor.createAccountMonitor
import cash.atto.commons.node.monitor.toAccountEntryMonitor
import cash.atto.commons.node.monitor.toTransactionMonitor
import cash.atto.commons.node.remote
import cash.atto.commons.toAttoHeight
import cash.atto.commons.toAttoIndex
import cash.atto.commons.toPrivateKey
import cash.atto.commons.toSeed
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.remote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AttoWalletTest {
    @Test
    fun test() =
        runTest(timeout = 300.seconds) {
            val seed = AttoMnemonic.generate().toSeed()
            val genesisAccountIndex = 0U.toAttoIndex()
            val accountIndex1 = 1U.toAttoIndex()
            val accountIndex2 = 2U.toAttoIndex()
            val accountIndex3 = 3U.toAttoIndex()
            val privateKey = seed.toPrivateKey(genesisAccountIndex)

            val node = AttoNodeMock.create(privateKey)
            val workServer = AttoWorkerMock.create()

            node.use {
                workServer.use {
                    node.start()
                    workServer.start()

                    val worker = AttoWorker.remote(workServer.baseUrl)
                    val client = AttoNodeClient.remote(node.baseUrl)
                    val accountMonitor = client.createAccountMonitor()

                    val initialHeights =
                        mapOf(
                            // skip genesis
                            node.genesisTransaction.address to node.genesisTransaction.block.height + 1U,
                        )

                    val accountEntryMonitor =
                        accountMonitor.toAccountEntryMonitor {
                            initialHeights[it] ?: 1U.toAttoHeight()
                        }
                    val transactionMonitor =
                        accountMonitor.toTransactionMonitor {
                            initialHeights[it] ?: 1U.toAttoHeight()
                        }

                    val wallet = AttoWallet.create(client, worker, seed)
                    val receiverJob =
                        wallet.startAutoReceiver(accountMonitor) {
                            AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32)))
                        }
                    wallet.openAccount(genesisAccountIndex)
                    wallet.openAccount(accountIndex1, accountIndex3)

                    assertEquals(AttoAmount.MAX, wallet.getAccount(genesisAccountIndex)!!.balance)
                    assertTrue(wallet.isOpen(accountIndex2))

                    val sendAmount = AttoAmount.from(AttoUnit.ATTO, "1")
                    val sendTransaction1 = wallet.send(genesisAccountIndex, wallet.getAddress(accountIndex2), sendAmount)
                    assertEquals(AttoAmount.MAX - sendAmount, wallet.getAccount(genesisAccountIndex)!!.balance)

                    val transactionMessage = transactionMonitor.stream().first()
                    transactionMessage.acknowledge()
                    assertEquals(sendTransaction1, transactionMessage.value)

//                    val accountEntryMessage = accountEntryMonitor.stream().first()
//                    accountEntryMessage.acknowledge()
//                    assertEquals(sendTransaction1.hash, accountEntryMessage.value.hash)

                    // TODO: add back when node is released
//                    val balance = withTimeoutOrNull(5.seconds) {
//                        while (wallet.getAccount(anotherAccountIndex)?.balance == null) {
//                            delay(1.seconds)
//                        }
//                        wallet.getAccount(anotherAccountIndex)?.balance
//                    }
//
//                    assertEquals(sendAmount, balance)
//
//                    receiverJob.cancel()
//                    val sendTransaction2 = wallet.send(genesisAccountIndex, wallet.getAddress(anotherAccountIndex), sendAmount)
//                    assertEquals(AttoAmount.MAX - (sendAmount + sendAmount), wallet.getAccount(genesisAccountIndex)!!.balance)
//
//                    val receivable = wallet.receivableFlow(minAmount = sendAmount).first()
//                    assertEquals(receivable, (sendTransaction2.block as AttoSendBlock).toReceivable())
                }
            }
        }
}
