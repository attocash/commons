package cash.atto.commons.node.monitor

import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.node.HeightSearch
import kotlinx.coroutines.flow.Flow

class AttoAccountEntryMonitor(
    private val nodeClient: AttoNodeClient,
    accountMonitor: AttoAccountMonitor,
    heightProvider: suspend (AttoAddress) -> AttoHeight = { AttoHeight.MIN },
) : AttoHeightMonitor<AttoAccountEntry>(accountMonitor, heightProvider) {
    override fun stream(search: HeightSearch): Flow<AttoAccountEntry> = nodeClient.accountEntryStream(search)
}

fun AttoAccountMonitor.toAccountEntryMonitor(
    heightProvider: suspend (AttoAddress) -> AttoHeight = {
        AttoHeight.MIN
    },
): AttoAccountEntryMonitor = AttoAccountEntryMonitor(client, this, heightProvider)
