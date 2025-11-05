package cash.atto.commons.node

import cash.atto.commons.AttoTransaction
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@JsExportForJs
actual class AttoNodeMockAsync actual constructor(
    private val mock: AttoNodeMock,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    actual val baseUrl: String
        get() = mock.baseUrl

    actual val genesisTransaction: AttoTransaction
        get() = mock.genesisTransaction

    actual fun start(): AttoFuture<Unit> = scope.submit { mock.start() }

    actual override fun close() {
        mock.close()
    }
}
