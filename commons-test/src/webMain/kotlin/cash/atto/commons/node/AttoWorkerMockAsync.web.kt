@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons.node

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.js.ExperimentalJsExport

@JsExportForJs
actual class AttoWorkerMockAsync internal actual constructor(
    private val mock: AttoWorkerMock,
    dispatcher: CoroutineDispatcher,
) {
    actual val baseUrl: String
        get() = mock.baseUrl

    suspend fun start(): Any = mock.start()

    suspend fun stop(): Any = mock.stop()

    actual fun close(): Unit = mock.close()
}
