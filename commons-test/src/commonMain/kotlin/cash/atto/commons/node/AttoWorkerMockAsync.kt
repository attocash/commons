package cash.atto.commons.node

import cash.atto.commons.AttoFuture
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@JsExportForJs
expect class AttoWorkerMockAsync internal constructor(
    mock: AttoWorkerMock,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val baseUrl: String

    fun start(): AttoFuture<Any>

    fun close()
}

fun AttoWorkerMock.toAsync(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoWorkerMockAsync =
    AttoWorkerMockAsync(this, dispatcher)
