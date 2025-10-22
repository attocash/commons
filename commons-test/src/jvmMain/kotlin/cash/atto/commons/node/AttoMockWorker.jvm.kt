package cash.atto.commons.node

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.PullPolicy

actual class AttoWorkerMock actual constructor(
    configuration: AttoWorkerMockConfiguration,
) : AutoCloseable {
    actual companion object {}

    private val worker =
        GenericContainer(configuration.image)
            .withNetworkAliases(configuration.name)
            .withExposedPorts(8080, 8081)
            .withImagePullPolicy(PullPolicy.alwaysPull())
            .waitingFor(Wait.forLogMessage(".*started on port 8080 \\(http\\).*\\n", 1))
            .withLogConsumer { frame: OutputFrame ->
                print(frame.utf8String)
            }

    actual val baseUrl: String
        get() {
            require(worker.isRunning) { "Work server must have started to access the url" }
            return "http://${worker.host}:${worker.getMappedPort(8080)}"
        }

    actual suspend fun start() {
        worker.start()
    }

    actual override fun close() {
        worker.close()
    }
}
