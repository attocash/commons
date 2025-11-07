package cash.atto.commons.node

import cash.atto.commons.AttoFuture
import cash.atto.commons.utils.JsExportForJs
import kotlin.js.ExperimentalJsExport

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
expect class AttoWorkerMockAsyncBuilder() {
    fun name(value: String): AttoWorkerMockAsyncBuilder

    fun image(value: String): AttoWorkerMockAsyncBuilder

    fun build(): AttoFuture<AttoWorkerMockAsync>
}
