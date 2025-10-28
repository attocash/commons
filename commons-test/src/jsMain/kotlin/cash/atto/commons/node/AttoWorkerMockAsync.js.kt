package cash.atto.commons.node

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@JsExportForJs
actual class AttoWorkerMockAsync actual constructor(
    private val mock: AttoWorkerMock,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    actual val baseUrl: String
        get() = mock.baseUrl

    actual fun start(): AttoFuture<Unit> = scope.submit { mock.start() }

    actual override fun close(): Unit = mock.close()
}
