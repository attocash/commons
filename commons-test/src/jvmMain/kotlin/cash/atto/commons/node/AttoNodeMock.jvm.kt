package cash.atto.commons.node

import cash.atto.commons.AttoTransaction
import cash.atto.commons.toHex
import org.testcontainers.Testcontainers
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.PullPolicy

private const val MYSQL_PORT = 3306

actual class AttoNodeMock actual constructor(
    private val configuration: AttoNodeMockConfiguration,
) : AutoCloseable {
    actual companion object {}

    private val mysql =
        MySQLContainer(configuration.mysqlImage)
            .withDatabaseName(configuration.dbName)
            .withUsername(configuration.dbUser)
            .withPassword(configuration.dbPassword)
            .apply {
                if (configuration.pullImages) {
                    withImagePullPolicy(PullPolicy.alwaysPull())
                }
            }

    private val node =
        GenericContainer(configuration.image)
            .withExposedPorts(8080, 8081, 8082)
            .withEnv("SPRING_PROFILES_ACTIVE", "local")
            .withEnv("ATTO_DB_NAME", configuration.dbName)
            .withEnv("ATTO_DB_USER", configuration.dbUser)
            .withEnv("ATTO_DB_PASSWORD", configuration.dbPassword)
            .withEnv("ATTO_PUBLIC_URI", "ws://${configuration.name}:8082")
            .withEnv("ATTO_GENESIS", configuration.genesisTransaction.toHex())
            .withEnv("ATTO_PRIVATE_KEY", configuration.privateKey.value.toHex())
            .withEnv("ATTO_NODE_FORCE_API", "true")
            .withEnv("ATTO_NODE_FORCE_HISTORICAL", "true")
            .waitingFor(Wait.forLogMessage(".*started on port 8080 \\(http\\).*\\n", 1))
            .apply {
                if (configuration.pullImages) {
                    withImagePullPolicy(PullPolicy.alwaysPull())
                }
                if (configuration.logOutput) {
                    withLogConsumer { frame: OutputFrame ->
                        print(frame.utf8String)
                    }
                }
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
        val mysqlPort = mysql.getMappedPort(MYSQL_PORT)
        Testcontainers.exposeHostPorts(mysqlPort)
        node
            .withEnv("ATTO_DB_HOST", GenericContainer.INTERNAL_HOST_HOSTNAME)
            .withEnv("ATTO_DB_PORT", mysqlPort.toString())
            .start()
    }

    actual override fun close() {
        node.close()
        mysql.close()
    }
}
