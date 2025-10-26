package cash.atto.commons.node

import cash.atto.commons.AttoTransaction

actual class AttoNodeMock actual constructor(
    configuration: AttoNodeMockConfiguration,
) : AutoCloseable {
    actual val baseUrl: String
        get() = TODO("Not yet implemented")


    actual val genesisTransaction: AttoTransaction
        get() = TODO("Not yet implemented")

    actual suspend fun start() {
        TODO("Not yet implemented")
    }

    actual override fun close() {
        TODO("Not yet implemented")
    }

    actual companion object

}
