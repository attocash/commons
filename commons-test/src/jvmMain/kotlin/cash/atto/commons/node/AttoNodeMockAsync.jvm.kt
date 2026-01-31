package cash.atto.commons.node

import cash.atto.commons.AttoTransaction
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

@JsExportForJs
actual class AttoNodeMockAsync internal actual constructor(
    private val mock: AttoNodeMock,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    actual val baseUrl: String
        get() = mock.baseUrl

    actual val genesisTransaction: AttoTransaction
        get() = mock.genesisTransaction

    fun start(): CompletableFuture<Any> = scope.future { mock.start() }

    actual override fun close() {
        try {
            mock.close()
        } finally {
            scope.cancel()
        }
    }
}
