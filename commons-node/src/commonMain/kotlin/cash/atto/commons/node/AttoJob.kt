package cash.atto.commons.node

class AttoJob(
    private val job: kotlinx.coroutines.Job,
) {
    fun isActive(): Boolean = job.isActive

    fun cancel() {
        job.cancel()
    }
}
