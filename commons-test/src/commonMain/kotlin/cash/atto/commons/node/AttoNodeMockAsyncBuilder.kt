package cash.atto.commons.node

import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoTransaction

expect class AttoNodeMockAsyncBuilder internal constructor(
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
