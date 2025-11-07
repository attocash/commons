package cash.atto.commons.node

import cash.atto.commons.AttoFuture
import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoTransaction
import cash.atto.commons.utils.JsExportForJs

@JsExportForJs
expect class AttoNodeMockAsyncBuilder(
    privateKey: AttoPrivateKey,
) {
    fun name(value: String): AttoNodeMockAsyncBuilder

    fun image(value: String): AttoNodeMockAsyncBuilder

    fun mysqlImage(value: String): AttoNodeMockAsyncBuilder

    fun dbName(value: String): AttoNodeMockAsyncBuilder

    fun dbUser(value: String): AttoNodeMockAsyncBuilder

    fun dbPassword(value: String): AttoNodeMockAsyncBuilder

    fun genesis(value: AttoTransaction?): AttoNodeMockAsyncBuilder

    fun build(): AttoFuture<AttoNodeMockAsync>
}
