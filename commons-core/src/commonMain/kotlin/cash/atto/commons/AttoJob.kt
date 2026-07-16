package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExportForJs
class AttoJob private constructor(
    private val activeProvider: () -> Boolean,
    private val cancellation: () -> Unit,
) {
    companion object {
        @OptIn(ExperimentalJsExport::class)
        @JsExport.Ignore
        fun create(
            activeProvider: () -> Boolean,
            cancellation: () -> Unit,
        ): AttoJob = AttoJob(activeProvider, cancellation)
    }

    fun isActive(): Boolean = activeProvider()

    fun cancel() {
        cancellation()
    }
}
