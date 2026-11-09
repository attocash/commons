package cash.atto.commons.node

import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoTransaction

internal class AttoNodeMockSettings(
    private val privateKey: AttoPrivateKey,
) {
    var name: String? = null
    var image: String? = null
    var mysqlImage: String? = null
    var dbName: String? = null
    var dbUser: String? = null
    var dbPassword: String? = null
    var pullImages: Boolean? = null
    var logOutput: Boolean? = null
    var genesis: AttoTransaction? = null

    suspend fun configuration(): AttoNodeMockConfiguration = configuration(genesis ?: AttoTransaction.createGenesis(privateKey))

    fun configuration(genesisTransaction: AttoTransaction): AttoNodeMockConfiguration {
        val defaults =
            AttoNodeMockConfiguration(
                genesisTransaction = genesisTransaction,
                privateKey = privateKey,
            )
        return defaults.copy(
            name = name ?: defaults.name,
            image = image ?: defaults.image,
            mysqlImage = mysqlImage ?: defaults.mysqlImage,
            dbName = dbName ?: defaults.dbName,
            dbUser = dbUser ?: defaults.dbUser,
            dbPassword = dbPassword ?: defaults.dbPassword,
            pullImages = pullImages ?: defaults.pullImages,
            logOutput = logOutput ?: defaults.logOutput,
        )
    }
}
