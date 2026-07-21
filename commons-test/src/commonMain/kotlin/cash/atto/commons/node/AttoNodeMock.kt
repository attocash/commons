package cash.atto.commons.node

import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoOpenBlock
import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoTransaction
import cash.atto.commons.utils.JsExportForJs
import cash.atto.commons.worker.AttoWorker
import kotlin.js.ExperimentalJsExport
import kotlin.jvm.JvmSynthetic

expect class AttoNodeMock internal constructor(
    configuration: AttoNodeMockConfiguration,
) : AutoCloseable {
    companion object {
        fun create(configuration: AttoNodeMockConfiguration): AttoNodeMock

        @JvmSynthetic
        suspend fun create(privateKey: AttoPrivateKey): AttoNodeMock
    }

    val baseUrl: String
    val genesisTransaction: AttoTransaction

    @JvmSynthetic
    suspend fun start()

    @JvmSynthetic
    suspend fun stop()

    override fun close()
}

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
data class AttoNodeMockConfiguration(
    val genesisTransaction: AttoTransaction,
    val privateKey: AttoPrivateKey,
    val name: String = "node",
    val image: String = "ghcr.io/attocash/node:live",
    val mysqlImage: String = "mysql:8.4",
    val dbName: String = "node",
    val dbUser: String = "root",
    val dbPassword: String = "root",
    val pullImages: Boolean = false,
    val logOutput: Boolean = false,
)

internal fun newAttoNodeMock(configuration: AttoNodeMockConfiguration): AttoNodeMock = AttoNodeMock(configuration)

@Deprecated(
    "Moved to AttoNodeMock.create(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoNodeMock.create(configuration)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoNodeMock.Companion.create(configuration: AttoNodeMockConfiguration): AttoNodeMock = AttoNodeMock.create(configuration)

suspend fun AttoTransaction.Companion.createGenesis(privateKey: AttoPrivateKey): AttoTransaction {
    val signer = privateKey.toSigner()
    val block = AttoOpenBlock.createGenesis(AttoNetwork.LOCAL, signer.address)
    return AttoTransaction(
        block,
        signer.sign(block),
        AttoWorker.cpu().use { it.work(block) },
    )
}

internal suspend fun newAttoNodeMock(privateKey: AttoPrivateKey): AttoNodeMock {
    val transaction = AttoTransaction.createGenesis(privateKey)
    return newAttoNodeMock(AttoNodeMockConfiguration(transaction, privateKey))
}

@Deprecated(
    "Moved to AttoNodeMock.create(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoNodeMock.create(privateKey)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun AttoNodeMock.Companion.create(privateKey: AttoPrivateKey): AttoNodeMock = AttoNodeMock.create(privateKey)
