package cash.atto.commons.node.monitor

import cash.atto.commons.node.AttoNodeClientAsync

expect class AttoAccountMonitorAsyncBuilder private constructor(
    nodeClient: AttoNodeClientAsync,
) {
    fun build(): AttoAccountMonitorAsync
}
