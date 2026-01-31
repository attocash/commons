package cash.atto.commons.node

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.js.ExperimentalJsExport

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
actual class AttoWorkerMockAsync internal actual constructor(
    private val mock: AttoWorkerMock,
    dispatcher: CoroutineDispatcher,
) {
    actual val baseUrl: String
        get() = mock.baseUrl

    suspend fun start(): Any = mock.start()

    actual fun close(): Unit = mock.close()
}
