package cash.atto.commons.node

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@JsExportForJs
expect class AttoWorkerMockAsync internal constructor(
    mock: AttoWorkerMock,
    dispatcher: CoroutineDispatcher,
) {
    val baseUrl: String

    fun close()
}

fun AttoWorkerMock.toAsync(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoWorkerMockAsync =
    AttoWorkerMockAsync(this, dispatcher)
