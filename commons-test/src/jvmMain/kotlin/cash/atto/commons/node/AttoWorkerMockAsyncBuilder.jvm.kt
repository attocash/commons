package cash.atto.commons.node

import cash.atto.commons.AttoFuture
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

@JsExportForJs
actual class AttoWorkerMockAsyncBuilder actual constructor() {
    private var name: String? = null
    private var image: String? = null

    actual fun name(value: String): AttoWorkerMockAsyncBuilder = apply { this.name = value }

    actual fun image(value: String): AttoWorkerMockAsyncBuilder = apply { this.image = value }

    fun build(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoFuture<AttoWorkerMockAsync> =
        CompletableFuture.completedFuture(
            run {
                val defaultConfiguration = AttoWorkerMockConfiguration()
                val configuration =
                    defaultConfiguration.copy(
                        name = name ?: defaultConfiguration.name,
                        image = image ?: defaultConfiguration.image,
                    )
                AttoWorkerMock(configuration).toAsync(dispatcher)
            },
        )

    actual fun build(): AttoFuture<AttoWorkerMockAsync> = build(Dispatchers.Default)

    fun build(executorService: ExecutorService): AttoFuture<AttoWorkerMockAsync> = build(executorService.asCoroutineDispatcher())
}
