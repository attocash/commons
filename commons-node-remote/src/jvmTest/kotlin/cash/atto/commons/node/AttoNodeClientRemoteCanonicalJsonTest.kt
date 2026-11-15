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
import cash.atto.commons.toAttoVersion
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AttoNodeClientRemoteCanonicalJsonTest {
    private val address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32) { 1 }))
    private val representative = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32) { 2 }))
    private val hash = AttoHash(ByteArray(32) { 3 })
    private val timestamp = AttoInstant.fromEpochMilliseconds(1_700_000_000_000)

    @Test
    fun `batch accounts use canonical v2 response identities`(): Unit =
        runBlocking {
            // Given
            val account =
                AttoAccount(
                    address,
                    AttoNetwork.LOCAL,
                    0U.toAttoVersion(),
                    AttoHeight(2UL),
                    AttoAmount(7UL),
                    hash,
                    timestamp,
                    representative,
                )
            val requests = CopyOnWriteArrayList<String>()
            val bodies = CopyOnWriteArrayList<String>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                requests += "${exchange.requestMethod} ${exchange.requestURI}"
                bodies += exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                val response = "[${account.toJson()}]".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            server.start()
            try {
                val client = AttoNodeClient.remote("http://127.0.0.1:${server.address.port}")

                // When
                val accounts = client.account(listOf(address))

                // Then
                assertEquals(listOf(account), accounts)
                assertEquals(listOf("POST /v2/accounts"), requests.toList())
                assertEquals("""{"addresses":["$address"]}""", bodies.single())
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `missing canonical batch route fails without legacy retry`(): Unit =
        runBlocking {
            // Given
            val requests = CopyOnWriteArrayList<String>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                requests += exchange.requestURI.path
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
            server.start()
            try {
                val client = AttoNodeClient.remote("http://127.0.0.1:${server.address.port}")

                // When
                val failure = runCatching { client.account(listOf(address)) }.exceptionOrNull()

                // Then
                assertNotNull(failure)
                assertEquals(listOf("/v2/accounts"), requests.toList())
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `scalar transaction reads accept canonical addresses with old numeric fields and extra keys`(): Unit =
        runBlocking {
            // Given
            val block =
                AttoSendBlock(
                    AttoNetwork.LOCAL,
                    0U.toAttoVersion(),
                    address,
                    AttoHeight(2UL),
                    AttoAmount(7UL),
                    timestamp,
                    hash,
                    representative,
                    AttoAmount(1UL),
                )
            val transaction = AttoTransaction(block, AttoSignature(ByteArray(64)), AttoWork(ByteArray(8)))
            val response =
                """
                {
                    "block": {
                        "type": "SEND",
                        "network": "LOCAL",
                        "version": 0,
                        "address": "$address",
                        "receiverAddress": "$representative",
                        "height": 2,
                        "balance": 7,
                        "amount": 1,
                        "timestamp": 1700000000000,
                        "previous": "$hash",
                        "algorithm": {},
                        "publicKey": "ignored",
                        "receiverAlgorithm": "V1",
                        "receiverPublicKey": "ignored"
                    },
                    "signature": "${"00".repeat(64)}",
                    "work": "${"00".repeat(8)}"
                }
                """.trimIndent().toByteArray()
            val requests = CopyOnWriteArrayList<String>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                requests += "${exchange.requestMethod} ${exchange.requestURI}"
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            server.start()
            try {
                val client = AttoNodeClient.remote("http://127.0.0.1:${server.address.port}")

                // When
                val decoded = client.transaction(hash)

                // Then
                assertEquals(transaction, decoded)
                assertEquals(listOf("GET /transactions/$hash"), requests.toList())
            } finally {
                server.stop(0)
            }
        }
}
