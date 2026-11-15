package cash.atto.commons.node

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoPublicKey
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AttoNodeClientRemoteAddressTest {
    @Test
    fun `single address queries encode the full address once in v2 HTTP routes`(): Unit =
        runBlocking {
            // Given
            val publicKey = "0101010101010101010101010101010101010101010101010101010101010101"
            val address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey.parse(publicKey))
            val encodedAddress = "atto%3A%2F%2F${address.path}"
            val requests = CopyOnWriteArrayList<String>()
            val decodedPaths = CopyOnWriteArrayList<String>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                requests += "${exchange.requestMethod} ${exchange.requestURI}"
                decodedPaths += exchange.requestURI.path
                val status = if (exchange.requestURI.rawPath == "/v2/accounts/$encodedAddress") 404 else 200
                exchange.sendResponseHeaders(status, -1)
                exchange.close()
            }
            server.start()
            try {
                val client = AttoNodeClient.remote("http://127.0.0.1:${server.address.port}")

                // When
                val account = client.account(address)
                client.accountStream(address).toList()
                client.receivableStream(address, AttoAmount(7U)).toList()
                client.accountEntryStream(address, AttoHeight(2UL), AttoHeight(5UL)).toList()
                client.accountEntryStream(address).toList()
                client.transactionStream(address, AttoHeight(3UL), AttoHeight(8UL)).toList()
                client.transactionStream(address).toList()

                // Then
                assertNull(account)
                assertEquals(
                    listOf(
                        "GET /v2/accounts/$encodedAddress",
                        "GET /v2/accounts/$encodedAddress/stream",
                        "GET /v2/accounts/$encodedAddress/receivables/stream?minAmount=7",
                        "GET /v2/accounts/$encodedAddress/entries/stream?fromHeight=2&toHeight=5",
                        "GET /v2/accounts/$encodedAddress/entries/stream?fromHeight=1",
                        "GET /v2/accounts/$encodedAddress/transactions/stream?fromHeight=3&toHeight=8",
                        "GET /v2/accounts/$encodedAddress/transactions/stream?fromHeight=1",
                    ),
                    requests.toList(),
                )
                assertEquals(
                    listOf(
                        "/v2/accounts/$address",
                        "/v2/accounts/$address/stream",
                        "/v2/accounts/$address/receivables/stream",
                        "/v2/accounts/$address/entries/stream",
                        "/v2/accounts/$address/entries/stream",
                        "/v2/accounts/$address/transactions/stream",
                        "/v2/accounts/$address/transactions/stream",
                    ),
                    decodedPaths.toList(),
                )
                assertFalse(requests.any { "%25" in it || "/$publicKey" in it })
            } finally {
                server.stop(0)
            }
        }
}
