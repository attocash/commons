package cash.atto.commons.spring

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.HeightSupport
import cash.atto.commons.toAttoHeight
import cash.atto.commons.toAttoVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ExtensionsTest {
    private val publicKey = AttoPublicKey(Random.Default.nextBytes(32))

    @Test
    fun `sortByHeight should sort stream`() {
        // given
        val account1 = create(1U)
        val account2 = create(2U)
        val account3 = create(3U)

        // when

        val sorted =
            runBlocking {
                arrayOf(account3, account1, account2)
                    .asFlow()
                    .sortByHeight(1U.toAttoHeight())
                    .toList()
            }

        // then
        assertThat(sorted).containsExactly(account1, account2, account3)
    }

    @Test
    fun `sortByHeight should deduplicate stream`() {
        // given
        val account1 = create(1U)
        val account2 = create(2U)
        val account3 = create(3U)

        // when

        val sorted =
            runBlocking {
                arrayOf(account3, account1, account1, account2, account3)
                    .asFlow()
                    .sortByHeight(1U.toAttoHeight())
                    .toList()
            }

        // then
        assertThat(sorted).containsExactly(account1, account2, account3)
    }

    @Test
    fun `sortByHeight should skip old heights`() {
        // given
        val account1 = create(1U)
        val account2 = create(2U)
        val account3 = create(3U)

        // when

        val sorted =
            runBlocking {
                arrayOf(account3, account1, account2)
                    .asFlow()
                    .sortByHeight(2U.toAttoHeight())
                    .toList()
            }

        // then
        assertThat(sorted).containsExactly(account2, account3)
    }

    @Test
    fun `forwardHeight should ignore late height`() {
        // given
        val account1 = create(1U)
        val account2 = create(2U)
        val account3 = create(3U)

        // when

        val sorted =
            runBlocking {
                arrayOf(account2, account1, account3)
                    .asFlow()
                    .forwardHeight()
                    .toList()
            }

        // then
        assertThat(sorted).containsExactly(account2, account3)
    }

    @Test
    fun `forwardHeightBy should track each key independently`() {
        // Given
        val first = HeightItem("first", 2U.toAttoHeight())
        val second = HeightItem("second", 1U.toAttoHeight())
        val secondNext = HeightItem("second", 3U.toAttoHeight())
        val firstNext = HeightItem("first", 3U.toAttoHeight())
        val values =
            listOf(
                HeightItem("first", 0U.toAttoHeight()),
                first,
                second,
                first,
                HeightItem("first", 1U.toAttoHeight()),
                secondNext,
                firstNext,
            )

        // When
        val forwarded =
            runBlocking {
                values.asFlow().forwardHeightBy { it.key }.toList()
            }

        // Then
        assertThat(forwarded).containsExactly(first, second, secondNext, firstNext)
    }

    @Test
    fun `height operators should start fresh for each collection`() {
        // Given
        val account1 = create(1U)
        val account2 = create(2U)
        val account3 = create(3U)
        val source = listOf(account3, account1, account2).asFlow()
        val flows =
            listOf(
                source.sortByHeight(1U.toAttoHeight()) to listOf(account1, account2, account3),
                source.forwardHeight() to listOf(account3),
                source.forwardHeightBy { it.address } to listOf(account3),
            )

        // When
        val collected =
            runBlocking {
                flows.map { (flow, expected) -> Triple(flow.toList(), flow.toList(), expected) }
            }

        // Then
        collected.forEach { (first, second, expected) ->
            assertThat(first).containsExactlyElementsOf(expected)
            assertThat(second).containsExactlyElementsOf(expected)
        }
    }

    @Test
    fun `height operators should cancel upstream when downstream stops`() {
        // Given
        val account1 = create(1U)
        val account2 = create(2U)
        val operators: List<(Flow<AttoAccount>) -> Flow<AttoAccount>> =
            listOf(
                { it.sortByHeight(1U.toAttoHeight()) },
                { it.forwardHeight() },
                { it.forwardHeightBy { account -> account.address } },
            )
        val cancellationResults = mutableListOf<Boolean>()
        val emitted = mutableListOf<List<AttoAccount>>()

        // When
        runBlocking {
            operators.forEach { operator ->
                var completed = false
                val source =
                    flow {
                        try {
                            emit(account1)
                            emit(account2)
                        } finally {
                            completed = true
                        }
                    }
                emitted.add(operator(source).take(1).toList())
                cancellationResults.add(completed)
            }
        }

        // Then
        emitted.forEach { assertThat(it).containsExactly(account1) }
        assertThat(cancellationResults).containsExactly(true, true, true)
    }

    private data class HeightItem(
        val key: String,
        override val height: AttoHeight,
    ) : HeightSupport

    private fun create(height: ULong): AttoAccount =
        AttoAccount(
            network = AttoNetwork.LOCAL,
            version = 0U.toAttoVersion(),
            address = AttoAddress(AttoAlgorithm.V1, publicKey),
            height = height.toAttoHeight(),
            balance = AttoAmount.MAX,
            lastTransactionHash = AttoHash(Random.Default.nextBytes(32)),
            lastTransactionTimestamp = AttoInstant.now(),
            representativeAddress = AttoAddress(AttoAlgorithm.V1, publicKey),
        )
}
