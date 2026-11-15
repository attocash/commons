@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.node

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoSendBlock
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoWork
import cash.atto.commons.node.monitor.createAccountMonitor
import cash.atto.commons.toAttoVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals

class AttoNodeClientRemoteWebSocketWebTest {
    @Test
    fun `publication uses HTTP with custom headers in Node JS and Wasm WebSocket mode`() =
        runTest {
            if (!isTypedNode()) return@runTest
            // Given
            val address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32) { 1 }))
            val transaction =
                AttoTransaction(
                    AttoSendBlock(
                        AttoNetwork.LOCAL,
                        0U.toAttoVersion(),
                        address,
                        AttoHeight(2UL),
                        AttoAmount(7UL),
                        AttoInstant.fromEpochMilliseconds(1_700_000_000_000),
                        AttoHash(ByteArray(32) { 2 }),
                        address,
                        AttoAmount(1UL),
                    ),
                    AttoSignature(ByteArray(64)),
                    AttoWork(ByteArray(8)),
                )
            val server = startPublicationServer().await()
            try {
                val client =
                    AttoNodeClient.remote("http://127.0.0.1:${typedPort(server)}", AttoNodeStreamTransport.WEBSOCKET) {
                        mapOf("Authorization" to "Bearer test")
                    }
                // When
                client.publish(transaction)
                // Then
                assertEquals("POST /v2/transactions Bearer test", publicationRequests(server))
                assertEquals(0, typedConnections(server))
            } finally {
                stopPublicationServer(server).await()
            }
        }

    @Test
    fun `typed monitor changes membership on one real Node JS and Wasm socket`() =
        runTest {
            if (!isTypedNode()) return@runTest
            // Given
            val first = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32) { 1 }))
            val second = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32) { 2 }))
            val accounts =
                listOf(first, second).map { address ->
                    AttoAccount(
                        address,
                        AttoNetwork.LOCAL,
                        0U.toAttoVersion(),
                        AttoHeight(2UL),
                        AttoAmount(7UL),
                        AttoHash(ByteArray(32) { 3 }),
                        AttoInstant.fromEpochMilliseconds(1_700_000_000_000),
                        second,
                    )
                }
            val server = startTypedServer("[${accounts.joinToString(",") { it.toJson() }}]").await()
            val received = Channel<AttoAccount>(8)
            try {
                val client = AttoNodeClient.remote("http://127.0.0.1:${typedPort(server)}", AttoNodeStreamTransport.WEBSOCKET)
                val monitor = client.createAccountMonitor()
                val collector = launch(Dispatchers.Default) { monitor.accountStream().collect { received.send(it) } }
                try {
                    awaitTypedUpdates(server, 1).await()
                    // When
                    monitor.monitor(first)
                    assertEquals(accounts[0], received.receive())
                    monitor.monitor(second)
                    assertEquals(accounts[1], received.receive())
                    monitor.stopMonitoring(first)
                    awaitTypedUpdates(server, 4).await()
                    monitor.stopMonitoring(second)
                    awaitTypedUpdates(server, 5).await()
                    monitor.monitor(first)
                    assertEquals(accounts[0], received.receive())
                    awaitTypedUpdates(server, 6).await()
                    collector.cancelAndJoin()
                    // Then
                    assertEquals(1, typedConnections(server))
                    assertEquals("$first,$second,$first", typedSnapshots(server))
                } finally {
                    collector.cancelAndJoin()
                }
            } finally {
                stopTypedServer(server).await()
            }
        }
}

private fun startTypedServer(accounts: String): Promise<JsAny> =
    js(
        """import('ws').then(({ WebSocketServer }) => new Promise(resolve => {
    const records = new Map(JSON.parse(accounts).map(account => [account.address, account]));
    const server = new WebSocketServer({ port: 0, host: '127.0.0.1' });
    server.connections = 0;
    server.updates = 0;
    server.snapshots = [];
    server.on('connection', socket => {
        server.connections++;
        let members = new Set();
        let sequence = 0;
        socket.on('message', data => {
            const command = JSON.parse(data.toString());
            if (command.type === 'subscribe' || command.type === 'update') {
                if (command.request.path !== '/v2/accounts/stream') throw new Error('Unexpected typed stream path');
                const next = new Set(command.request.body.addresses);
                for (const address of next) {
                    if (!members.has(address)) {
                        server.snapshots.push(address);
                        socket.send(JSON.stringify({type: 'data', data: records.get(address), sequence: String(++sequence)}));
                    }
                }
                members = next;
                socket.send(JSON.stringify({type: command.type === 'subscribe' ? 'subscribed' : 'updated'}));
                server.updates++;
            }
        });
    });
    server.on('listening', () => resolve(server));
}))""",
    )

private fun typedPort(server: JsAny): Int = js("server.address().port")

private fun typedConnections(server: JsAny): Int = js("server.connections")

private fun typedSnapshots(server: JsAny): String = js("server.snapshots.join(',')")

private fun awaitTypedUpdates(
    server: JsAny,
    count: Int,
): Promise<JsAny> =
    js(
        """new Promise((resolve, reject) => {
    const deadline = Date.now() + 5000;
    function check() {
        if (server.updates >= count) resolve(server);
        else if (Date.now() > deadline) reject(new Error('Membership update timeout'));
        else setTimeout(check, 1);
    }
    check();
})""",
    )

private fun stopTypedServer(server: JsAny): Promise<JsAny> =
    js(
        """new Promise(resolve => {
    for (const socket of server.clients) socket.terminate();
    server.close(() => resolve(null));
})""",
    )

private fun isTypedNode(): Boolean = js("typeof process !== 'undefined' && process.versions != null && process.versions.node != null")

private fun startPublicationServer(): Promise<JsAny> =
    js(
        """import('node:http').then(({createServer}) => new Promise(resolve => {
    const server = createServer((request, response) => {
        server.requests.push(request.method + ' ' + request.url + ' ' + request.headers.authorization);
        let body = '';
        request.on('data', chunk => body += chunk);
        request.on('end', () => {
            response.writeHead(200, {'Content-Type': 'application/x-ndjson'});
            response.end('\n' + body + '\n');
        });
    });
    server.connections = 0;
    server.requests = [];
    server.on('upgrade', (request, socket) => { server.connections++; socket.destroy(); });
    server.listen(0, '127.0.0.1', () => resolve(server));
}))""",
    )

private fun publicationRequests(server: JsAny): String = js("server.requests.join(',')")

private fun stopPublicationServer(server: JsAny): Promise<JsAny> = js("new Promise(resolve => server.close(() => resolve(null)))")
