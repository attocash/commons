package cash.atto.commons.node

import cash.atto.commons.AttoTransaction
import cash.atto.commons.toHex
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.PullPolicy

actual class AttoNodeMock actual constructor(
    private val configuration: AttoNodeMockConfiguration,
) : AutoCloseable {
    actual companion object {}

    private val network = Network.newNetwork()

    private val mysql =
        MySQLContainer(configuration.mysqlImage)
            .withNetwork(network)
            .withNetworkAliases("mysql")
            .withDatabaseName(configuration.dbName)
            .withUsername(configuration.dbUser)
            .withPassword(configuration.dbPassword)
            .withImagePullPolicy(PullPolicy.alwaysPull())

    private val node =
        GenericContainer(configuration.image)
            .withNetwork(network)
            .withNetworkAliases(configuration.name)
            .withExposedPorts(8080, 8081, 8082)
            .withEnv("SPRING_PROFILES_ACTIVE", "local")
            .withEnv("ATTO_DB_HOST", "mysql")
            .withEnv("ATTO_DB_PORT", "3306")
            .withEnv("ATTO_DB_NAME", configuration.dbName)
            .withEnv("ATTO_DB_USER", configuration.dbUser)
            .withEnv("ATTO_DB_PASSWORD", configuration.dbPassword)
            .withEnv("ATTO_PUBLIC_URI", "ws://${configuration.name}:8082")
            .withEnv("ATTO_GENESIS", configuration.genesisTransaction.toHex())
            .withEnv("ATTO_PRIVATE_KEY", configuration.privateKey.value.toHex())
            .withEnv("ATTO_NODE_FORCE_API", "true")
            .withEnv("ATTO_NODE_FORCE_HISTORICAL", "true")
            .withImagePullPolicy(PullPolicy.alwaysPull())
            .waitingFor(Wait.forLogMessage(".*started on port 8080 \\(http\\).*\\n", 1))
            .withLogConsumer { frame: OutputFrame ->
                print(frame.utf8String)
            }

    actual val baseUrl: String
        get() {
            require(node.isRunning) { "Node must have started to access the url" }
            return "http://${node.host}:${node.getMappedPort(8080)}"
        }

    actual val genesisTransaction: AttoTransaction
        get() = configuration.genesisTransaction

    actual suspend fun start() {
        mysql.start()
        node.start()
    }

    actual override fun close() {
        node.close()
        mysql.close()
        network.close()
    }
}
