@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.node

import cash.atto.commons.AttoTransaction
import cash.atto.commons.toHex
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

actual class AttoNodeMock actual constructor(
    private val configuration: AttoNodeMockConfiguration,
) : AutoCloseable {
    private var network: JsAny? = null
    private var mysqlContainer: JsAny? = null
    private var nodeContainer: JsAny? = null
    private var started = false

    actual val baseUrl: String
        get() {
            require(started) { "Node must have started to access the url" }
            val host = getHost(nodeContainer!!)
            val port = getMappedPort(nodeContainer!!, 8080)
            return "http://$host:$port"
        }

    actual val genesisTransaction: AttoTransaction
        get() = configuration.genesisTransaction

    actual suspend fun start() {
        configureTestcontainersRuntime()

        try {
            val testcontainersModule = importTestcontainers().awaitTestcontainers()
            val mysqlModule = importMySql().awaitTestcontainers()
            val wait = getWait(testcontainersModule)

            val networkInstance = createNetwork(testcontainersModule)
            network = startNetwork(networkInstance).awaitTestcontainers()

            val mysqlInstance = createMySqlContainer(mysqlModule, configuration.mysqlImage)
            withMySqlNetwork(mysqlInstance, network!!)
            withMySqlNetworkAliases(mysqlInstance)
            withMySqlDatabase(mysqlInstance, configuration.dbName)
            withMySqlRootPassword(mysqlInstance, configuration.dbPassword)
            mysqlContainer = startMySqlContainer(mysqlInstance).awaitTestcontainers()

            val nodeInstance = createGenericContainer(testcontainersModule, configuration.image)
            withNodeNetwork(nodeInstance, network!!)
            withNodeNetworkAliases(nodeInstance, configuration.name)
            withNodeExposedPorts(nodeInstance)
            withNodeEnvironment(
                nodeInstance,
                configuration.dbName,
                configuration.dbUser,
                configuration.dbPassword,
                configuration.name,
                configuration.genesisTransaction.toHex(),
                configuration.privateKey.value.toHex(),
            )
            withNodeWaitStrategy(nodeInstance, wait)
            if (configuration.logOutput) {
                withNodeLogConsumer(nodeInstance)
            }
            nodeContainer = startNodeContainer(nodeInstance).awaitTestcontainers()

            started = true
        } catch (exception: Throwable) {
            try {
                stop()
            } catch (cleanupException: Throwable) {
                exception.addSuppressed(cleanupException)
            }
            throw exception
        }
    }

    actual suspend fun stop() {
        val resources = takeResources() ?: return
        stopTestcontainersResources(resources.node, resources.mysql, resources.network)
    }

    actual override fun close() {
        val resources = takeResources() ?: return
        scheduleTestcontainersCleanup(resources.node, resources.mysql, resources.network)
    }

    private fun takeResources(): AttoNodeMockResources? {
        if (nodeContainer == null && mysqlContainer == null && network == null) {
            return null
        }

        val resources = AttoNodeMockResources(nodeContainer, mysqlContainer, network)
        nodeContainer = null
        mysqlContainer = null
        network = null
        started = false
        return resources
    }

    actual companion object {
        actual fun create(configuration: AttoNodeMockConfiguration): AttoNodeMock = newAttoNodeMock(configuration)

        actual suspend fun create(privateKey: cash.atto.commons.AttoPrivateKey): AttoNodeMock = newAttoNodeMock(privateKey)
    }
}

private data class AttoNodeMockResources(
    val node: JsAny?,
    val mysql: JsAny?,
    val network: JsAny?,
)

private fun importTestcontainers(): Promise<JsAny> = js("import('testcontainers')")

private fun importMySql(): Promise<JsAny> = js("import('@testcontainers/mysql')")

private fun getWait(testcontainers: JsAny): JsAny = js("testcontainers.Wait")

private fun createNetwork(testcontainers: JsAny): JsAny = js("new testcontainers.Network()")

private fun startNetwork(network: JsAny): Promise<JsAny> = js("network.start()")

private fun createMySqlContainer(
    mysqlModule: JsAny,
    image: String,
): JsAny = js("new mysqlModule.MySqlContainer(image)")

private fun withMySqlNetwork(
    container: JsAny,
    network: JsAny,
): JsAny = js("container.withNetwork(network)")

private fun withMySqlNetworkAliases(container: JsAny): JsAny = js("container.withNetworkAliases('mysql')")

private fun withMySqlDatabase(
    container: JsAny,
    dbName: String,
): JsAny = js("container.withDatabase(dbName)")

private fun withMySqlRootPassword(
    container: JsAny,
    password: String,
): JsAny = js("container.withRootPassword(password)")

private fun startMySqlContainer(container: JsAny): Promise<JsAny> = js("container.start()")

private fun createGenericContainer(
    testcontainers: JsAny,
    image: String,
): JsAny = js("new testcontainers.GenericContainer(image)")

private fun withNodeNetwork(
    container: JsAny,
    network: JsAny,
): JsAny = js("container.withNetwork(network)")

private fun withNodeNetworkAliases(
    container: JsAny,
    name: String,
): JsAny = js("container.withNetworkAliases(name)")

private fun withNodeExposedPorts(container: JsAny): JsAny = js("container.withExposedPorts(8080, 8081, 8082)")

private fun withNodeEnvironment(
    container: JsAny,
    dbName: String,
    dbUser: String,
    dbPassword: String,
    nodeName: String,
    genesisHex: String,
    privateKeyHex: String,
): JsAny =
    js(
        """
        (function() {
            var env = {};
            env['SPRING_PROFILES_ACTIVE'] = 'local';
            env['ATTO_DB_HOST'] = 'mysql';
            env['ATTO_DB_PORT'] = '3306';
            env['ATTO_DB_NAME'] = dbName;
            env['ATTO_DB_USER'] = dbUser;
            env['ATTO_DB_PASSWORD'] = dbPassword;
            env['ATTO_PUBLIC_URI'] = 'ws://' + nodeName + ':8082';
            env['ATTO_GENESIS'] = genesisHex;
            env['ATTO_PRIVATE_KEY'] = privateKeyHex;
            env['ATTO_NODE_FORCE_API'] = 'true';
            env['ATTO_NODE_FORCE_HISTORICAL'] = 'true';
            return container.withEnvironment(env);
        })()
        """,
    )

private fun withNodeWaitStrategy(
    container: JsAny,
    wait: JsAny,
): JsAny = js("container.withWaitStrategy(wait.forLogMessage(/.*started on port 8080 \\(http\\).*/, 1))")

private fun withNodeLogConsumer(container: JsAny): JsAny =
    js(
        "container.withLogConsumer(function(stream) { stream.on('data', function(line) { console.log(line.toString()); }); stream.on('err', function(line) { console.error(line.toString()); }); })",
    )

private fun startNodeContainer(container: JsAny): Promise<JsAny> = js("container.start()")

private fun getHost(container: JsAny): String = js("container.getHost()")

private fun getMappedPort(
    container: JsAny,
    port: Int,
): Int = js("container.getMappedPort(port)")
