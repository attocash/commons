package cash.atto.commons.node

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.promise
import kotlin.js.Promise

@OptIn(ExperimentalWasmJsInterop::class)
actual class AttoFuture<T> internal constructor(
    internal val promise: Promise<JsAny?>,
) {
    @Suppress("UNCHECKED_CAST")
    fun asPromise() = promise as Promise<T>
}

@OptIn(ExperimentalWasmJsInterop::class)
actual fun <T> CoroutineScope.submit(block: suspend () -> T): AttoFuture<T> {
    val p: Promise<JsAny?> = promise { block() }
    return AttoFuture(p)
}
