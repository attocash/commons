package cash.atto.commons.node

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher

@JsExportForJs
actual class AttoWorkerMockAsync internal actual constructor(
    private val mock: AttoWorkerMock,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    actual val baseUrl: String
        get() = mock.baseUrl

    suspend fun start(): Any = mock.start()

    actual override fun close(): Unit = mock.close()
}
