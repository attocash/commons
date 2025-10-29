package cash.atto.commons.node

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.promise
import kotlin.js.Promise

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
actual class AttoFuture<T> internal constructor(
    private val promise: Promise<T>,
) {
    fun asPromise() = promise
}

actual fun <T> CoroutineScope.submit(block: suspend () -> T): AttoFuture<T> = AttoFuture(promise { block() })
