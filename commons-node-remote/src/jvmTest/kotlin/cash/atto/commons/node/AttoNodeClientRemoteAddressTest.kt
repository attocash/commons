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
import kotlin.test.assertNull

class AttoNodeClientRemoteAddressTest {
    @Test
    fun `single address queries preserve public key HTTP routes`() =
        runBlocking {
            // Given
            val publicKey = "0101010101010101010101010101010101010101010101010101010101010101"
            val address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey.parse(publicKey))
            val requests = CopyOnWriteArrayList<String>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                requests += "${exchange.requestMethod} ${exchange.requestURI}"
                val status = if (exchange.requestURI.path == "/accounts/$publicKey") 404 else 200
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
                        "GET /accounts/$publicKey",
                        "GET /accounts/$publicKey/stream",
                        "GET /accounts/$publicKey/receivables/stream?minAmount=7",
                        "GET /accounts/$publicKey/entries/stream?fromHeight=2&toHeight=5",
                        "GET /accounts/$publicKey/entries/stream?fromHeight=1",
                        "GET /accounts/$publicKey/transactions/stream?fromHeight=3&toHeight=8",
                        "GET /accounts/$publicKey/transactions/stream?fromHeight=1",
                    ),
                    requests.toList(),
                )
            } finally {
                server.stop(0)
            }
        }
}
