package cash.atto.commons.node

import kotlinx.coroutines.await
import kotlin.js.Promise

actual class AttoWorkerMock internal actual constructor(
    private val configuration: AttoWorkerMockConfiguration,
) : AutoCloseable {
    private var container: dynamic = null
    private var started = false

    actual val baseUrl: String
        get() {
            require(started) { "Work server must have started to access the url" }
            val host = container.getHost() as String
            val port = container.getMappedPort(8080) as Int
            return "http://$host:$port"
        }

    actual suspend fun start() {
        val testcontainersPromise: dynamic = js("import('testcontainers')")
        val testcontainers = testcontainersPromise.unsafeCast<Promise<dynamic>>().await()

        val wait = testcontainers.Wait

        val image = configuration.image
        val containerInstance =
            js("new testcontainers.GenericContainer(image)")
                .withExposedPorts(js("8080"), js("8081"))
                .withWaitStrategy(wait.forLogMessage(js("/.*started on port 8080 \\(http\\).*/"), js("1")))
                .withLogConsumer(
                    js(
                        "function(stream) { stream.on('data', function(line) { console.log(line.toString()); }); stream.on('err', function(line) { console.error(line.toString()); }); }",
                    ),
                )

        val containerPromise = containerInstance.start().unsafeCast<Promise<dynamic>>()
        container = containerPromise.await()
        started = true
    }

    actual override fun close() {
        if (started && container != null) {
            container.stop()
            started = false
        }
    }

    actual companion object
}
