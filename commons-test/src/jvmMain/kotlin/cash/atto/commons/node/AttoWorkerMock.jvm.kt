package cash.atto.commons.node

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.PullPolicy
import kotlin.jvm.JvmSynthetic

actual class AttoWorkerMock actual constructor(
    configuration: AttoWorkerMockConfiguration,
) : AutoCloseable {
    actual companion object {
        actual fun create(configuration: AttoWorkerMockConfiguration): AttoWorkerMock = newAttoWorkerMock(configuration)
    }

    private val worker =
        GenericContainer(configuration.image)
            .withExposedPorts(8080, 8081)
            .waitingFor(Wait.forLogMessage(".*started on port 8080 \\(http\\).*\\n", 1))
            .apply {
                if (configuration.pullImage) {
                    withImagePullPolicy(PullPolicy.alwaysPull())
                }
                if (configuration.logOutput) {
                    withLogConsumer { frame: OutputFrame ->
                        print(frame.utf8String)
                    }
                }
            }

    actual val baseUrl: String
        get() {
            require(worker.isRunning) { "Work server must have started to access the url" }
            return "http://${worker.host}:${worker.getMappedPort(8080)}"
        }

    @JvmSynthetic
    actual suspend fun start() {
        worker.start()
    }

    @JvmSynthetic
    actual suspend fun stop() {
        close()
    }

    actual override fun close() {
        worker.close()
    }
}
