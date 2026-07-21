@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons.node

import cash.atto.commons.AttoTransaction
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.js.ExperimentalJsExport

@JsExportForJs
actual class AttoNodeMockAsync internal actual constructor(
    private val mock: AttoNodeMock,
    dispatcher: CoroutineDispatcher,
) {
    actual val baseUrl: String
        get() = mock.baseUrl

    actual val genesisTransaction: AttoTransaction
        get() = mock.genesisTransaction

    suspend fun start(): Any = mock.start()

    suspend fun stop(): Any = mock.stop()

    actual fun close() {
        mock.close()
    }
}
