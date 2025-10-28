package cash.atto.commons.node

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope

@OptIn(ExperimentalJsExport::class)
@JsExportForJs
internal actual class AttoWorkerMockAsyncBuilder actual constructor() {
    private var name: String? = null
    private var image: String? = null

    actual fun name(value: String): AttoWorkerMockAsyncBuilder = apply { this.name = value }

    actual fun image(value: String): AttoWorkerMockAsyncBuilder = apply { this.image = value }

    @OptIn(DelicateCoroutinesApi::class)
    actual fun build(): AttoFuture<AttoWorkerMockAsync> = GlobalScope.submit {
        val defaultConfiguration = AttoWorkerMockConfiguration()
        val configuration = defaultConfiguration.copy(
            name = name ?: defaultConfiguration.name,
            image = image ?: defaultConfiguration.image,
        )
        AttoWorkerMock(configuration).toAsync(Dispatchers.Default)
    }
}
