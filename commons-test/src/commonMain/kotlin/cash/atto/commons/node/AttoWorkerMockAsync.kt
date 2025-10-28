package cash.atto.commons.node

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

expect class AttoWorkerMockAsync internal constructor(
    mock: AttoWorkerMock,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {
    val baseUrl: String

    fun start(): AttoFuture<Unit>

    override fun close()
}

fun AttoWorkerMock.toAsync(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoWorkerMockAsync =
    AttoWorkerMockAsync(this, dispatcher)
