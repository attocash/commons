package cash.atto.commons.node

import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoTransaction
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutorService

@JsExportForJs
actual class AttoNodeMockAsyncBuilder actual constructor(
    privateKey: AttoPrivateKey,
) {
    private val settings = AttoNodeMockSettings(privateKey)

    actual fun name(value: String): AttoNodeMockAsyncBuilder = apply { settings.name = value }

    actual fun image(value: String): AttoNodeMockAsyncBuilder = apply { settings.image = value }

    actual fun mysqlImage(value: String): AttoNodeMockAsyncBuilder = apply { settings.mysqlImage = value }

    actual fun dbName(value: String): AttoNodeMockAsyncBuilder = apply { settings.dbName = value }

    actual fun dbUser(value: String): AttoNodeMockAsyncBuilder = apply { settings.dbUser = value }

    actual fun dbPassword(value: String): AttoNodeMockAsyncBuilder = apply { settings.dbPassword = value }

    actual fun pullImages(value: Boolean): AttoNodeMockAsyncBuilder = apply { settings.pullImages = value }

    actual fun logOutput(value: Boolean): AttoNodeMockAsyncBuilder = apply { settings.logOutput = value }

    actual fun genesis(value: AttoTransaction?): AttoNodeMockAsyncBuilder = apply { settings.genesis = value }

    @JvmOverloads
    fun build(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoNodeMockAsync {
        val configuration =
            settings.genesis?.let { settings.configuration(it) } ?: runBlocking { settings.configuration() }
        val mock = AttoNodeMock(configuration)
        return AttoNodeMockAsync(mock, dispatcher)
    }

    fun build(executorService: ExecutorService): AttoNodeMockAsync = build(executorService.asCoroutineDispatcher())
}
