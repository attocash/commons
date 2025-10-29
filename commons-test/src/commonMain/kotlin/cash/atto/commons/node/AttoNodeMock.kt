package cash.atto.commons.node

import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoOpenBlock
import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoTransaction
import cash.atto.commons.createGenesis
import cash.atto.commons.toSigner
import cash.atto.commons.utils.JsExportForJs
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.cpu
import kotlin.js.ExperimentalJsExport

expect class AttoNodeMock internal constructor(
    configuration: AttoNodeMockConfiguration,
) : AutoCloseable {
    companion object

    val baseUrl: String
    val genesisTransaction: AttoTransaction

    suspend fun start()

    override fun close()
}

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
data class AttoNodeMockConfiguration(
    val genesisTransaction: AttoTransaction,
    val privateKey: AttoPrivateKey,
    val name: String = "node",
    // TODO: change to live
    val image: String = "ghcr.io/attocash/node:main",
    val mysqlImage: String = "mysql:8.4",
    val dbName: String = "node",
    val dbUser: String = "root",
    val dbPassword: String = "root",
)

fun AttoNodeMock.Companion.create(configuration: AttoNodeMockConfiguration): AttoNodeMock {
    return AttoNodeMock(configuration)
}

suspend fun AttoTransaction.Companion.createGenesis(privateKey: AttoPrivateKey): AttoTransaction {
    val signer = privateKey.toSigner()
    val block = AttoOpenBlock.createGenesis(AttoNetwork.LOCAL, signer.address)
    return AttoTransaction(
        block,
        signer.sign(block.hash),
        AttoWorker.cpu().use { it.work(block) },
    )
}

suspend fun AttoNodeMock.Companion.create(privateKey: AttoPrivateKey): AttoNodeMock {
    val transaction = AttoTransaction.createGenesis(privateKey)
    return AttoNodeMock.create(AttoNodeMockConfiguration(transaction, privateKey))
}
