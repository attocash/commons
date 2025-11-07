package cash.atto.commons

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future

actual typealias AttoFuture<T> = java.util.concurrent.CompletableFuture<T>

actual fun <T> CoroutineScope.submit(block: suspend () -> T): AttoFuture<T> = future { block() }

actual suspend fun <T> AttoFuture<T>.await(): T = this.await()
