package cash.atto.commons.node

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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class AttoNodeClientRemoteStreamRoutesTest {
    @Test
    fun `batch accounts and streams use v2 while other scalar routes stay unchanged`(): Unit =
        runBlocking {
            // Given
            val address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32) { 1 }))
            val hash = AttoHash(ByteArray(32) { 2 })
            val search = HeightSearch(listOf(AccountHeightSearch(address, AttoHeight(2UL), AttoHeight(5UL))))
            val transaction = transaction(address, hash)
            val requests = CopyOnWriteArrayList<String>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                requests += "${exchange.requestMethod} ${exchange.requestURI}"
                val body = exchange.requestBody.readBytes()
                when {
                    exchange.requestMethod == "POST" && exchange.requestURI.path == "/v2/transactions" -> {
                        val response = "\n \t\n${body.toString(Charsets.UTF_8)}\n\n".toByteArray()
                        exchange.responseHeaders.add("Content-Type", "application/x-ndjson")
                        exchange.sendResponseHeaders(200, response.size.toLong())
                        exchange.responseBody.write(response)
                    }

                    exchange.requestURI.path == "/v2/accounts" -> {
                        val response = "[]".toByteArray()
                        exchange.responseHeaders.add("Content-Type", "application/json")
                        exchange.sendResponseHeaders(200, response.size.toLong())
                        exchange.responseBody.write(response)
                    }

                    exchange.requestURI.path.startsWith("/v2/") -> {
                        exchange.sendResponseHeaders(200, -1)
                    }

                    else -> {
                        exchange.sendResponseHeaders(404, -1)
                    }
                }
                exchange.close()
            }
            server.start()
            try {
                val client = AttoNodeClient.remote("http://127.0.0.1:${server.address.port}")

                // When
                client.accountStream().toList()
                client.accountStream(listOf(address)).toList()
                client.receivableStream(listOf(address), AttoAmount(7UL)).toList()
                client.accountEntryStream().toList()
                client.accountEntryStream(hash).toList()
                client.accountEntryStream(search).toList()
                client.transactionStream().toList()
                client.transactionStream(hash).toList()
                client.transactionStream(search).toList()
                client.publish(transaction)
                client.account(listOf(address))
                assertFails { client.transaction(hash) }
                assertFails { client.now(AttoInstant.fromEpochMilliseconds(1)) }
                assertFails { client.voterWeight(address) }

                // Then
                assertEquals(
                    listOf(
                        "GET /v2/accounts/stream",
                        "POST /v2/accounts/stream",
                        "POST /v2/accounts/receivables/stream?minAmount=7",
                        "GET /v2/accounts/entries/stream",
                        "GET /v2/accounts/entries/$hash/stream",
                        "POST /v2/accounts/entries/stream",
                        "GET /v2/transactions/stream",
                        "GET /v2/transactions/$hash/stream",
                        "POST /v2/accounts/transactions/stream",
                        "POST /v2/transactions",
                        "POST /v2/accounts",
                        "GET /transactions/$hash",
                        "GET /instants/${AttoInstant.fromEpochMilliseconds(1)}",
                        "GET /vote-weights/${address.path}",
                    ),
                    requests.toList(),
                )
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `missing v2 stream fails without retrying a legacy route`(): Unit =
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
                assertFails { client.transactionStream().toList() }

                // Then
                assertEquals(listOf("/v2/transactions/stream"), requests.toList())
            } finally {
                server.stop(0)
            }
        }

    private fun transaction(
        address: AttoAddress,
        previous: AttoHash,
    ): AttoTransaction =
        AttoTransaction(
            block =
                AttoSendBlock(
                    network = AttoNetwork.LOCAL,
                    version = 0U.toAttoVersion(),
                    address = address,
                    height = AttoHeight(2UL),
                    balance = AttoAmount(1UL),
                    timestamp = AttoInstant.fromEpochMilliseconds(1),
                    previous = previous,
                    receiverAddress = address,
                    amount = AttoAmount(1UL),
                ),
            signature = AttoSignature(ByteArray(AttoSignature.SIZE)),
            work = AttoWork(ByteArray(AttoWork.SIZE)),
        )
}
