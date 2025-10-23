package cash.atto.commons.node

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.promise
import kotlin.js.Promise

actual class AttoFuture<T> internal constructor(
    internal val promise: Promise<T>,
) {
    fun asPromise() = promise
}

actual fun <T> CoroutineScope.submit(block: suspend () -> T): AttoFuture<T> = AttoFuture(promise { block() })
