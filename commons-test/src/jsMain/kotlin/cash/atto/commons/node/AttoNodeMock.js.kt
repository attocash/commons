package cash.atto.commons.node

import cash.atto.commons.AttoTransaction
import cash.atto.commons.toHex
import kotlinx.coroutines.await
import kotlin.js.Promise

actual class AttoNodeMock actual constructor(
    private val configuration: AttoNodeMockConfiguration,
) : AutoCloseable {
    private var network: dynamic = null
    private var mysqlContainer: dynamic = null
    private var nodeContainer: dynamic = null
    private var started = false

    actual val baseUrl: String
        get() {
            require(started) { "Node must have started to access the url" }
            val host = nodeContainer.getHost() as String
            val port = nodeContainer.getMappedPort(8080) as Int
            return "http://$host:$port"
        }

    actual val genesisTransaction: AttoTransaction
        get() = configuration.genesisTransaction

    actual suspend fun start() {
        val testcontainersPromise: dynamic = js("import('testcontainers')")
        val testcontainers = testcontainersPromise.unsafeCast<Promise<dynamic>>().await()

        val mysqlPromise: dynamic = js("import('@testcontainers/mysql')")
        val mysqlModule = mysqlPromise.unsafeCast<Promise<dynamic>>().await()

        val wait = testcontainers.Wait

        // Create network
        val networkInstance = js("new testcontainers.Network()")
        val networkPromise = networkInstance.start().unsafeCast<Promise<dynamic>>()
        network = networkPromise.await()

        // Start MySQL container
        val mysqlImage = configuration.mysqlImage
        val mysqlInstance =
            js("new mysqlModule.MySqlContainer(mysqlImage)")
                .withNetwork(network)
                .withNetworkAliases(js("'mysql'"))
                .withDatabase(configuration.dbName)
                .withRootPassword(configuration.dbPassword)
        val mysqlContainerPromise = mysqlInstance.start().unsafeCast<Promise<dynamic>>()
        mysqlContainer = mysqlContainerPromise.await()

        // Start node container
        val genesisHex = configuration.genesisTransaction.toHex()
        val privateKeyHex = configuration.privateKey.value.toHex()

        // Build environment object dynamically
        val env = js("({})")
        env["SPRING_PROFILES_ACTIVE"] = "local"
        env["ATTO_DB_HOST"] = "mysql"
        env["ATTO_DB_PORT"] = "3306"
        env["ATTO_DB_NAME"] = configuration.dbName
        env["ATTO_DB_USER"] = configuration.dbUser
        env["ATTO_DB_PASSWORD"] = configuration.dbPassword
        env["ATTO_PUBLIC_URI"] = "ws://${configuration.name}:8082"
        env["ATTO_GENESIS"] = genesisHex
        env["ATTO_PRIVATE_KEY"] = privateKeyHex
        env["ATTO_NODE_FORCE_API"] = "true"
        env["ATTO_NODE_FORCE_HISTORICAL"] = "true"

        val nodeImage = configuration.image
        val nodeInstance =
            js("new testcontainers.GenericContainer(nodeImage)")
                .withNetwork(network)
                .withNetworkAliases(configuration.name)
                .withExposedPorts(js("8080"), js("8081"), js("8082"))
                .withEnvironment(env)
                .withWaitStrategy(wait.forLogMessage(js("/.*started on port 8080 \\(http\\).*/"), js("1")))
                .withLogConsumer(
                    js(
                        "function(stream) { stream.on('data', function(line) { console.log(line.toString()); }); stream.on('err', function(line) { console.error(line.toString()); }); }",
                    ),
                )
        val nodeContainerPromise = nodeInstance.start().unsafeCast<Promise<dynamic>>()
        nodeContainer = nodeContainerPromise.await()

        started = true
    }

    actual override fun close() {
        if (started) {
            nodeContainer?.stop()
            mysqlContainer?.stop()
            network?.stop()
            started = false
        }
    }

    actual companion object
}
