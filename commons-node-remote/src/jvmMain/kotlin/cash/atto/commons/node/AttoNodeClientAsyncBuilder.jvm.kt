package cash.atto.commons.node

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService

actual class AttoNodeClientAsyncBuilder actual constructor(
    actual val url: String,
) {
    actual var headers: Map<String, String> = emptyMap()

    actual companion object {
        @JvmStatic
        actual fun remote(url: String): AttoNodeClientAsyncBuilder = AttoNodeClientAsyncBuilder(url)
    }

    actual fun headers(value: Map<String, String>) = apply { headers = value }

    actual fun header(
        name: String,
        value: String,
    ) = apply { headers = headers + (name to value) }

    fun build(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoNodeClientAsync =
        AttoNodeClient
            .remote(url, {
                headers
            })
            .toAsync(dispatcher)

    actual fun build(): AttoNodeClientAsync = build(Dispatchers.Default)

    fun build(executorService: ExecutorService): AttoNodeClientAsync = build(executorService.asCoroutineDispatcher())
}
