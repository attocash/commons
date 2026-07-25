package cash.atto.commons.node

internal fun closeTestcontainersResources(vararg resources: AutoCloseable?) {
    var failure: Throwable? = null
    for (resource in resources) {
        if (resource == null) {
            continue
        }

        try {
            resource.close()
        } catch (exception: Throwable) {
            if (failure == null) {
                failure = exception
            } else {
                failure.addSuppressed(exception)
            }
        }
    }

    failure?.let { throw it }
}
