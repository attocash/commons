package cash.atto.commons

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalMultiplatform::class)
expect class AttoFuture<T> : Any

expect fun <T> CoroutineScope.submit(block: suspend () -> T): AttoFuture<T>

expect suspend fun <T> AttoFuture<T>.await(): T

internal inline fun <T> CoroutineScope.consumeStream(
    stream: Flow<T>,
    crossinline onEach: suspend (T) -> Unit,
    noinline onCancel: (Exception?) -> Unit,
): AttoJob =
    AttoJob(
        launch {
            try {
                stream.collect { onEach(it) }
                onCancel(null)
            } catch (e: CancellationException) {
                onCancel(null)
                throw e
            } catch (e: Exception) {
                onCancel(e)
            }
        },
    )
