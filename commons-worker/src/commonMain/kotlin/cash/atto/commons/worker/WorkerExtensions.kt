package cash.atto.commons.worker

import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMultiplatform::class)
expect class AttoFuture<T>

expect fun <T> CoroutineScope.submit(block: suspend () -> T): AttoFuture<T>
