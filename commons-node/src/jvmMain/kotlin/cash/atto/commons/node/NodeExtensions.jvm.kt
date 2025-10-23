package cash.atto.commons.node

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future

actual typealias AttoFuture<T> = java.util.concurrent.CompletableFuture<T>

actual fun <T> CoroutineScope.submit(block: suspend () -> T): AttoFuture<T> = future { block() }
