package cash.atto.commons.node

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.js.ExperimentalJsExport

@OptIn(ExperimentalJsExport::class)
expect class AttoNodeClientAsync(
    client: AttoNodeClient,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {
    val client: AttoNodeClient

    override fun close()
}

fun AttoNodeClient.toAsync(dispatcher: CoroutineDispatcher): AttoNodeClientAsync = AttoNodeClientAsync(this, dispatcher)
