package cash.atto.commons.node

import cash.atto.commons.AttoFuture
import cash.atto.commons.AttoTransaction
import cash.atto.commons.submit
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
actual class AttoNodeMockAsync internal actual constructor(
    private val mock: AttoNodeMock,
    dispatcher: CoroutineDispatcher,
) {
    private val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    actual val baseUrl: String
        get() = mock.baseUrl

    actual val genesisTransaction: AttoTransaction
        get() = mock.genesisTransaction

    actual fun start(): AttoFuture<Any> = scope.submit { mock.start() }

    actual fun close() {
        mock.close()
    }
}
