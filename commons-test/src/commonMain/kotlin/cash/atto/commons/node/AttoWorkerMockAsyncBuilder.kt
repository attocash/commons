package cash.atto.commons.node

import cash.atto.commons.utils.JsExportForJs

@JsExportForJs
internal expect class AttoWorkerMockAsyncBuilder internal constructor() {
    fun name(value: String): AttoWorkerMockAsyncBuilder

    fun image(value: String): AttoWorkerMockAsyncBuilder

    fun build(): AttoFuture<AttoWorkerMockAsync>
}
