@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons.node

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.Dispatchers
import kotlin.js.ExperimentalJsExport

@JsExportForJs
actual class AttoWorkerMockAsyncBuilder actual constructor() {
    private val settings = AttoWorkerMockSettings()

    actual fun name(value: String): AttoWorkerMockAsyncBuilder = apply { settings.name = value }

    actual fun image(value: String): AttoWorkerMockAsyncBuilder = apply { settings.image = value }

    actual fun pullImage(value: Boolean): AttoWorkerMockAsyncBuilder = apply { settings.pullImage = value }

    actual fun logOutput(value: Boolean): AttoWorkerMockAsyncBuilder = apply { settings.logOutput = value }

    suspend fun build(): AttoWorkerMockAsync = AttoWorkerMock(settings.configuration()).toAsync(Dispatchers.Default)
}
