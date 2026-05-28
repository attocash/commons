package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs

@JsExportForJs
class AttoJob(
    private val job: kotlinx.coroutines.Job,
) {
    fun isActive(): Boolean = job.isActive

    fun cancel() {
        job.cancel()
    }
}
