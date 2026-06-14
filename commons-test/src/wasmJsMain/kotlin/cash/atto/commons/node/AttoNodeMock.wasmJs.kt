package cash.atto.commons.node

import cash.atto.commons.AttoTransaction
import cash.atto.commons.toHex
import kotlinx.coroutines.await
import kotlin.js.Promise

private const val MYSQL_PORT = 3306

@OptIn(ExperimentalWasmJsInterop::class)
actual class AttoNodeMock actual constructor(
    private val configuration: AttoNodeMockConfiguration,
) : AutoCloseable {
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
        val testcontainersModule = importTestcontainers().await<JsAny>()
        val mysqlModule = importMySql().await<JsAny>()
        val wait = getWait(testcontainersModule)

        // Start MySQL container
        val mysqlImage = configuration.mysqlImage
        val mysqlInstance = createMySqlContainer(mysqlModule, mysqlImage)
        withMySqlDatabase(mysqlInstance, configuration.dbName)
        withMySqlRootPassword(mysqlInstance, configuration.dbPassword)
        mysqlContainer = startMySqlContainer(mysqlInstance).await<JsAny>()
        val mysqlPort = getMappedPort(mysqlContainer!!, MYSQL_PORT)
        exposeHostPorts(testcontainersModule, mysqlPort).await<JsAny?>()

        // Start node container
        val genesisHex = configuration.genesisTransaction.toHex()
        val privateKeyHex = configuration.privateKey.value.toHex()

        val nodeImage = configuration.image
        val nodeInstance = createGenericContainer(testcontainersModule, nodeImage)
        withNodeExposedPorts(nodeInstance)
        withNodeEnvironment(
            nodeInstance,
            configuration.dbName,
            configuration.dbUser,
            configuration.dbPassword,
            configuration.name,
            mysqlPort,
            genesisHex,
            privateKeyHex,
        )
        withNodeWaitStrategy(nodeInstance, wait)
        if (configuration.logOutput) {
            withNodeLogConsumer(nodeInstance)
        }
        nodeContainer = startNodeContainer(nodeInstance).await<JsAny>()

        started = true
    }

    actual override fun close() {
        if (started) {
            nodeContainer?.let { stopContainer(it) }
            mysqlContainer?.let { stopContainer(it) }
            started = false
        }
    }

    actual companion object
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun importTestcontainers(): Promise<JsAny> = js("import('testcontainers')")

@OptIn(ExperimentalWasmJsInterop::class)
private fun importMySql(): Promise<JsAny> = js("import('@testcontainers/mysql')")

@OptIn(ExperimentalWasmJsInterop::class)
private fun getWait(testcontainers: JsAny): JsAny = js("testcontainers.Wait")

@OptIn(ExperimentalWasmJsInterop::class)
private fun exposeHostPorts(
    testcontainers: JsAny,
    port: Int,
): Promise<JsAny?> = js("testcontainers.TestContainers.exposeHostPorts(port)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun createMySqlContainer(
    mysqlModule: JsAny,
    image: String,
): JsAny = js("new mysqlModule.MySqlContainer(image)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun withMySqlDatabase(
    container: JsAny,
    dbName: String,
): JsAny = js("container.withDatabase(dbName)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun withMySqlRootPassword(
    container: JsAny,
    password: String,
): JsAny = js("container.withRootPassword(password)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun startMySqlContainer(container: JsAny): Promise<JsAny> = js("container.start()")

@OptIn(ExperimentalWasmJsInterop::class)
private fun createGenericContainer(
    testcontainers: JsAny,
    image: String,
): JsAny = js("new testcontainers.GenericContainer(image)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun withNodeExposedPorts(container: JsAny): JsAny = js("container.withExposedPorts(8080, 8081, 8082)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun withNodeEnvironment(
    container: JsAny,
    dbName: String,
    dbUser: String,
    dbPassword: String,
    nodeName: String,
    mysqlPort: Int,
    genesisHex: String,
    privateKeyHex: String,
): JsAny =
    js(
        """
    (function() {
        var env = {};
        env['SPRING_PROFILES_ACTIVE'] = 'local';
        env['ATTO_DB_HOST'] = 'host.testcontainers.internal';
        env['ATTO_DB_PORT'] = String(mysqlPort);
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

@OptIn(ExperimentalWasmJsInterop::class)
private fun withNodeWaitStrategy(
    container: JsAny,
    wait: JsAny,
): JsAny = js("container.withWaitStrategy(wait.forLogMessage(/.*started on port 8080 \\(http\\).*/, 1))")

@OptIn(ExperimentalWasmJsInterop::class)
private fun withNodeLogConsumer(container: JsAny): JsAny =
    js(
        "container.withLogConsumer(function(stream) { stream.on('data', function(line) { console.log(line.toString()); }); stream.on('err', function(line) { console.error(line.toString()); }); })",
    )

@OptIn(ExperimentalWasmJsInterop::class)
private fun startNodeContainer(container: JsAny): Promise<JsAny> = js("container.start()")

@OptIn(ExperimentalWasmJsInterop::class)
private fun getHost(container: JsAny): String = js("container.getHost()")

@OptIn(ExperimentalWasmJsInterop::class)
private fun getMappedPort(
    container: JsAny,
    port: Int,
): Int = js("container.getMappedPort(port)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun stopContainer(container: JsAny) {
    js("container.stop()")
}
