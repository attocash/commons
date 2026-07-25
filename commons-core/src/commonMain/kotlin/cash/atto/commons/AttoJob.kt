package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExportForJs
class AttoJob private constructor(
    private val activeProvider: () -> Boolean,
    private val cancellation: () -> Unit,
    private val cancellationAndJoin: suspend () -> Unit,
) {
    companion object {
        @OptIn(ExperimentalJsExport::class)
        @JsExport.Ignore
        fun create(
            activeProvider: () -> Boolean,
            cancellation: () -> Unit,
        ): AttoJob = AttoJob(activeProvider, cancellation) { cancellation() }

        @OptIn(ExperimentalJsExport::class)
        @JsExport.Ignore
        fun create(
            activeProvider: () -> Boolean,
            cancellation: () -> Unit,
            cancellationAndJoin: suspend () -> Unit,
        ): AttoJob = AttoJob(activeProvider, cancellation, cancellationAndJoin)
    }

    fun isActive(): Boolean = activeProvider()

    fun cancel() {
        cancellation()
    }

    /**
     * Cancels this job and waits until its work and cancellation handlers complete.
     */
    suspend fun cancelAndJoin() {
        cancellationAndJoin()
    }
}
