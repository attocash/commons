package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoTransaction
import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.node.HeightSearch
import kotlinx.coroutines.flow.Flow

class AttoTransactionMonitor(
    private val nodeClient: AttoNodeClient,
    accountMonitor: AttoAccountMonitor,
    heightProvider: suspend (AttoAddress) -> AttoHeight = { AttoHeight.MIN },
) : AttoHeightMonitor<AttoTransaction>(accountMonitor, heightProvider) {
    override fun stream(search: HeightSearch): Flow<AttoTransaction> = nodeClient.transactionStream(search)
}

fun AttoAccountMonitor.toTransactionMonitor(
    heightProvider: (AttoAddress) -> AttoHeight = {
        AttoHeight.MIN
    },
): AttoTransactionMonitor = AttoTransactionMonitor(client, this, heightProvider)
