package cash.atto.commons.node

expect class AttoWorkerMock internal constructor(
    configuration: AttoWorkerMockConfiguration,
) : AutoCloseable {
    companion object

    val baseUrl: String

    suspend fun start()

    override fun close()
}

data class AttoWorkerMockConfiguration(
    val name: String = "worker-server",
    val image: String = "ghcr.io/attocash/work-server:cpu",
)

fun AttoWorkerMock.Companion.create(configuration: AttoWorkerMockConfiguration = AttoWorkerMockConfiguration()): AttoWorkerMock =
    AttoWorkerMock(
        configuration,
    )
