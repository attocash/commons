package cash.atto.commons.node

import kotlin.jvm.JvmSynthetic

expect class AttoWorkerMock internal constructor(
    configuration: AttoWorkerMockConfiguration,
) : AutoCloseable {
    companion object {
        fun create(configuration: AttoWorkerMockConfiguration = AttoWorkerMockConfiguration()): AttoWorkerMock
    }

    val baseUrl: String

    @JvmSynthetic
    suspend fun start()

    @JvmSynthetic
    suspend fun stop()

    override fun close()
}

data class AttoWorkerMockConfiguration(
    val name: String = "worker-server",
    val image: String = "ghcr.io/attocash/work-server:cpu",
    val pullImage: Boolean = false,
    val logOutput: Boolean = false,
)

internal fun newAttoWorkerMock(configuration: AttoWorkerMockConfiguration): AttoWorkerMock =
    AttoWorkerMock(
        configuration,
    )

@Deprecated(
    "Moved to AttoWorkerMock.create(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoWorkerMock.create(configuration)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoWorkerMock.Companion.create(configuration: AttoWorkerMockConfiguration = AttoWorkerMockConfiguration()): AttoWorkerMock =
    AttoWorkerMock.create(configuration)
