package cash.atto.commons.worker

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher

@JsExportForJs
expect class AttoWorkerAsync internal constructor(
    worker: AttoWorker,
    dispatcher: CoroutineDispatcher,
) {
    val worker: AttoWorker
}

fun AttoWorker.toAsync(dispatcher: CoroutineDispatcher): AttoWorkerAsync = AttoWorkerAsync(this, dispatcher)
