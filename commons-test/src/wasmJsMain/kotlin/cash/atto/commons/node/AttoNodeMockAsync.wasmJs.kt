package cash.atto.commons.node

import cash.atto.commons.AttoTransaction
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher

@JsExportForJs
actual class AttoNodeMockAsync internal actual constructor(
    private val mock: AttoNodeMock,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    actual val baseUrl: String
        get() = mock.baseUrl

    actual val genesisTransaction: AttoTransaction
        get() = mock.genesisTransaction

    suspend fun start(): Any = mock.start()

    actual override fun close() {
        mock.close()
    }
}
