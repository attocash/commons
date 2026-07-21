package cash.atto.commons.node

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

@JsExportForJs
actual class AttoWorkerMockAsync internal actual constructor(
    private val mock: AttoWorkerMock,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    actual val baseUrl: String
        get() = mock.baseUrl

    fun start(): CompletableFuture<Any> = scope.future { mock.start() }

    fun stop(): CompletableFuture<Any> = scope.future { mock.stop() }

    actual override fun close() {
        try {
            mock.close()
        } finally {
            scope.cancel()
        }
    }
}
