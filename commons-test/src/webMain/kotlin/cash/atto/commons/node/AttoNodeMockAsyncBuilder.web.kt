@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons.node

import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoTransaction
import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.Dispatchers
import kotlin.js.ExperimentalJsExport

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

    suspend fun build(): AttoNodeMockAsync {
        val configuration = settings.configuration()
        return AttoNodeMock(configuration).toAsync(Dispatchers.Default)
    }
}
