package cash.atto.commons.node

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService

@JsExportForJs
actual class AttoWorkerMockAsyncBuilder actual constructor() {
    private var name: String? = null
    private var image: String? = null

    actual fun name(value: String): AttoWorkerMockAsyncBuilder = apply { this.name = value }

    actual fun image(value: String): AttoWorkerMockAsyncBuilder = apply { this.image = value }

    @JvmOverloads
    fun build(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoWorkerMockAsync {
        val defaultConfiguration = AttoWorkerMockConfiguration()
        val configuration =
            defaultConfiguration.copy(
                name = name ?: defaultConfiguration.name,
                image = image ?: defaultConfiguration.image,
            )

        return AttoWorkerMock(configuration).toAsync(dispatcher)
    }

    fun build(executorService: ExecutorService): AttoWorkerMockAsync = build(executorService.asCoroutineDispatcher())
}
