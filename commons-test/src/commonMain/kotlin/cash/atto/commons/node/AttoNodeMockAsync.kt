package cash.atto.commons.node

import cash.atto.commons.AttoFuture
import cash.atto.commons.AttoTransaction
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@JsExportForJs
expect class AttoNodeMockAsync internal constructor(
    mock: AttoNodeMock,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val baseUrl: String
    val genesisTransaction: AttoTransaction

    fun start(): AttoFuture<Any>

    fun close()
}

fun AttoNodeMock.toAsync(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoNodeMockAsync = AttoNodeMockAsync(this, dispatcher)
