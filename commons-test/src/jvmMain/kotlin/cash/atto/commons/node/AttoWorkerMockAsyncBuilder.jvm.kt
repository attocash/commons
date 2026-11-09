package cash.atto.commons.node

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService

@JsExportForJs
actual class AttoWorkerMockAsyncBuilder actual constructor() {
    private val settings = AttoWorkerMockSettings()

    actual fun name(value: String): AttoWorkerMockAsyncBuilder = apply { settings.name = value }

    actual fun image(value: String): AttoWorkerMockAsyncBuilder = apply { settings.image = value }

    actual fun pullImage(value: Boolean): AttoWorkerMockAsyncBuilder = apply { settings.pullImage = value }

    actual fun logOutput(value: Boolean): AttoWorkerMockAsyncBuilder = apply { settings.logOutput = value }

    @JvmOverloads
    fun build(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoWorkerMockAsync =
        AttoWorkerMock(settings.configuration()).toAsync(dispatcher)

    fun build(executorService: ExecutorService): AttoWorkerMockAsync = build(executorService.asCoroutineDispatcher())
}
