package cash.atto.commons.node

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlin.js.Promise

@OptIn(ExperimentalWasmJsInterop::class)
@JsExportForJs
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

@OptIn(ExperimentalWasmJsInterop::class)
actual suspend fun <T> AttoFuture<T>.await(): T {
    @Suppress("UNCHECKED_CAST")
    return this.promise.await() as T
}
