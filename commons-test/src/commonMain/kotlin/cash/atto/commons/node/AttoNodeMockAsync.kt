package cash.atto.commons.node

import cash.atto.commons.AttoTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

expect class AttoNodeMockAsync internal constructor(
    mock: AttoNodeMock,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {
    val baseUrl: String
    val genesisTransaction: AttoTransaction

    fun start(): AttoFuture<Unit>

    override fun close()
}

fun AttoNodeMock.toAsync(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoNodeMockAsync =
    AttoNodeMockAsync(this, dispatcher)
