package cash.atto.commons.worker

internal class WebWorkerResources : AutoCloseable {
    private val releases = mutableListOf<() -> Unit>()

    fun <T> acquire(
        resource: T,
        release: (T) -> Unit,
    ): T {
        releases.add { release(resource) }
        return resource
    }

    fun <T> initialize(action: () -> T): T =
        try {
            action()
        } catch (failure: Throwable) {
            closeOnFailure(failure)
        }

    fun closeOnFailure(failure: Throwable): Nothing {
        try {
            close()
        } catch (cleanupFailure: Throwable) {
            failure.addSuppressed(cleanupFailure)
        }
        throw failure
    }

    override fun close() {
        val pending = releases.toList()
        releases.clear()
        var failure: Throwable? = null
        for (release in pending.asReversed()) {
            try {
                release()
            } catch (cleanupFailure: Throwable) {
                if (failure == null) {
                    failure = cleanupFailure
                } else {
                    failure.addSuppressed(cleanupFailure)
                }
            }
        }
        failure?.let { throw it }
    }
}
