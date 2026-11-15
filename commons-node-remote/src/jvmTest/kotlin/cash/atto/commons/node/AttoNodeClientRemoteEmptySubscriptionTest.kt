package cash.atto.commons.node

import cash.atto.commons.AttoAddress
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttoNodeClientRemoteEmptySubscriptionTest {
    @Test
    fun `empty dynamic updates complete without opening a socket`(): Unit =
        runBlocking {
            // Given
            val client = AttoNodeClient.remote("http://127.0.0.1:1", AttoNodeStreamTransport.WEBSOCKET)
            // When
            val accounts = client.accountStream(emptyFlow<Collection<AttoAddress>>()).toList()
            val receivables = client.receivableStream(emptyFlow<Collection<AttoAddress>>()).toList()
            val entries = client.accountEntryStream(emptyFlow<HeightSearch>()).toList()
            val transactions = client.transactionStream(emptyFlow<HeightSearch>()).toList()
            // Then
            assertEquals(emptyList(), accounts)
            assertEquals(emptyList(), receivables)
            assertEquals(emptyList(), entries)
            assertEquals(emptyList(), transactions)
            assertTrue(client.supportsSubscriptionUpdates)
            assertFalse(AttoNodeClient.remote("http://127.0.0.1:1").supportsSubscriptionUpdates)
        }
}
