package cash.atto.commons.node

import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope

actual class AttoNodeMockAsyncBuilder actual constructor(
    private val privateKey: AttoPrivateKey,
) {
    private var name: String? = null
    private var image: String? = null
    private var mysqlImage: String? = null
    private var dbName: String? = null
    private var dbUser: String? = null
    private var dbPassword: String? = null
    private var genesis: AttoTransaction? = null

    actual fun name(value: String): AttoNodeMockAsyncBuilder = apply { name = value }

    actual fun image(value: String): AttoNodeMockAsyncBuilder = apply { image = value }

    actual fun mysqlImage(value: String): AttoNodeMockAsyncBuilder = apply { mysqlImage = value }

    actual fun dbName(value: String): AttoNodeMockAsyncBuilder = apply { dbName = value }

    actual fun dbUser(value: String): AttoNodeMockAsyncBuilder = apply { dbUser = value }

    actual fun dbPassword(value: String): AttoNodeMockAsyncBuilder = apply { dbPassword = value }

    actual fun genesis(value: AttoTransaction?): AttoNodeMockAsyncBuilder = apply { genesis = value }

    @OptIn(DelicateCoroutinesApi::class)
    fun build(dispatcher: CoroutineDispatcher = Dispatchers.Default): AttoFuture<AttoNodeMockAsync> =
        GlobalScope.submit {
            val defaultConfiguration =
                AttoNodeMockConfiguration(
                    genesisTransaction =
                        genesis ?: AttoTransaction.createGenesis(privateKey),
                    privateKey = privateKey,
                )
            val configuration =
                defaultConfiguration.copy(
                    name = name ?: defaultConfiguration.name,
                    image = image ?: defaultConfiguration.image,
                    mysqlImage = mysqlImage ?: defaultConfiguration.mysqlImage,
                    dbName = dbName ?: defaultConfiguration.dbName,
                    dbUser = dbUser ?: defaultConfiguration.dbUser,
                    dbPassword = dbPassword ?: defaultConfiguration.dbPassword,
                )
            val mock = AttoNodeMock(configuration)

            AttoNodeMockAsync(mock, dispatcher)
        }

    actual fun build(): AttoFuture<AttoNodeMockAsync> = build(Dispatchers.Default)
}
