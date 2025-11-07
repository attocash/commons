package cash.atto.commons.node.monitor

import cash.atto.commons.node.AttoNodeClientAsync
import cash.atto.commons.utils.JsExportForJs

@JsExportForJs
expect class AttoAccountMonitorAsyncBuilder private constructor(
    nodeClient: AttoNodeClientAsync,
) {
    fun build(): AttoAccountMonitorAsync
}
