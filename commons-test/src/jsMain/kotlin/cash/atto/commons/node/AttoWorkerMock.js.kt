package cash.atto.commons.node

actual class AttoWorkerMock internal actual constructor(
    configuration: AttoWorkerMockConfiguration,
) : AutoCloseable {
    actual val baseUrl: String
        get() = TODO("Not yet implemented")

    actual suspend fun start() {
        TODO("Not yet implemented")
    }

    actual override fun close() {
        TODO("Not yet implemented")
    }

    actual companion object
}
