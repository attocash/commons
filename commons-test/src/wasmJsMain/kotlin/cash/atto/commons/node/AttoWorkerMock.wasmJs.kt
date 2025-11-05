package cash.atto.commons.node

import kotlinx.coroutines.await
import kotlin.js.Promise

@OptIn(ExperimentalWasmJsInterop::class)
actual class AttoWorkerMock internal actual constructor(
    private val configuration: AttoWorkerMockConfiguration,
) : AutoCloseable {
    private var container: JsAny? = null
    private var started = false

    actual val baseUrl: String
        get() {
            require(started) { "Work server must have started to access the url" }
            val host = getHost(container!!)
            val port = getMappedPort(container!!, 8080)
            return "http://$host:$port"
        }

    actual suspend fun start() {
        val testcontainersModule = importTestcontainers().await<JsAny>()
        val wait = getWait(testcontainersModule)

        val image = configuration.image
        val containerInstance = createGenericContainer(testcontainersModule, image)
        withExposedPorts(containerInstance, 8080, 8081)
        withWaitStrategy(containerInstance, wait)
        withLogConsumer(containerInstance)

        container = startContainer(containerInstance).await<JsAny>()
        started = true
    }

    actual override fun close() {
        if (started && container != null) {
            stopContainer(container!!)
            started = false
        }
    }

    actual companion object
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun importTestcontainers(): Promise<JsAny> = js("import('testcontainers')")

@OptIn(ExperimentalWasmJsInterop::class)
private fun getWait(testcontainers: JsAny): JsAny = js("testcontainers.Wait")

@OptIn(ExperimentalWasmJsInterop::class)
private fun createGenericContainer(
    testcontainers: JsAny,
    image: String,
): JsAny = js("new testcontainers.GenericContainer(image)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun withExposedPorts(
    container: JsAny,
    port1: Int,
    port2: Int,
): JsAny = js("container.withExposedPorts(port1, port2)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun withWaitStrategy(
    container: JsAny,
    wait: JsAny,
): JsAny = js("container.withWaitStrategy(wait.forLogMessage(/.*started on port 8080 \\(http\\).*/, 1))")

@OptIn(ExperimentalWasmJsInterop::class)
private fun withLogConsumer(container: JsAny): JsAny =
    js(
        "container.withLogConsumer(function(stream) { stream.on('data', function(line) { console.log(line.toString()); }); stream.on('err', function(line) { console.error(line.toString()); }); })",
    )

@OptIn(ExperimentalWasmJsInterop::class)
private fun startContainer(container: JsAny): Promise<JsAny> = js("container.start()")

@OptIn(ExperimentalWasmJsInterop::class)
private fun getHost(container: JsAny): String = js("container.getHost()")

@OptIn(ExperimentalWasmJsInterop::class)
private fun getMappedPort(
    container: JsAny,
    port: Int,
): Int = js("container.getMappedPort(port)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun stopContainer(container: JsAny) {
    js("container.stop()")
}
