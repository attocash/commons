@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.node

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

actual class AttoWorkerMock internal actual constructor(
    private val configuration: AttoWorkerMockConfiguration,
) : AutoCloseable {
    private val lifecycle = TestcontainersLifecycle("Work server mock")
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
        lifecycle.beginStart()
        try {
            configureTestcontainersRuntime()
            awaitScheduledTestcontainersCleanup()
            val testcontainersModule = importTestcontainers().awaitTestcontainers()
            val wait = getWait(testcontainersModule)

            val containerInstance = createGenericContainer(testcontainersModule, configuration.image)
            withExposedPorts(containerInstance, 8080, 8081)
            withWaitStrategy(containerInstance, wait)
            if (configuration.logOutput) {
                withLogConsumer(containerInstance)
            }

            container = startContainer(containerInstance).awaitTestcontainers()
            started = true
        } catch (exception: Throwable) {
            try {
                stop()
            } catch (cleanupException: Throwable) {
                exception.addSuppressed(cleanupException)
            }
            throw exception
        }
    }

    actual suspend fun stop() {
        val worker = takeContainer()
        stopTestcontainersResources(worker)
    }

    actual override fun close() {
        val worker = takeContainer()
        scheduleTestcontainersCleanup(worker)
    }

    private fun takeContainer(): JsAny? {
        val worker = container
        container = null
        started = false
        lifecycle.release()
        return worker
    }

    actual companion object {
        actual fun create(configuration: AttoWorkerMockConfiguration): AttoWorkerMock = newAttoWorkerMock(configuration)
    }
}

private fun importTestcontainers(): Promise<JsAny> = js("import('testcontainers')")

private fun getWait(testcontainers: JsAny): JsAny = js("testcontainers.Wait")

private fun createGenericContainer(
    testcontainers: JsAny,
    image: String,
): JsAny = js("new testcontainers.GenericContainer(image)")

private fun withExposedPorts(
    container: JsAny,
    port1: Int,
    port2: Int,
): JsAny = js("container.withExposedPorts(port1, port2)")

private fun withWaitStrategy(
    container: JsAny,
    wait: JsAny,
): JsAny = js("container.withWaitStrategy(wait.forLogMessage(/.*started on port 8080 \\(http\\).*/, 1))")

private fun withLogConsumer(container: JsAny): JsAny =
    js(
        "container.withLogConsumer(function(stream) { stream.on('data', function(line) { console.log(line.toString()); }); stream.on('err', function(line) { console.error(line.toString()); }); })",
    )

private fun startContainer(container: JsAny): Promise<JsAny> = js("container.start()")

private fun getHost(container: JsAny): String = js("container.getHost()")

private fun getMappedPort(
    container: JsAny,
    port: Int,
): Int = js("container.getMappedPort(port)")
