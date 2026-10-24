package cash.atto.commons

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttoNumericSerializationTest {
    @Test
    fun `should encode amounts as decimal strings and accept legacy numbers`() {
        // Given
        val values = listOf(0UL, 1UL, 9_007_199_254_740_992UL, 9_007_199_254_740_993UL, AttoAmount.MAX.raw - 1UL, AttoAmount.MAX.raw)

        // When
        val amounts = values.map { AttoAmount(it) }

        // Then
        amounts.forEach { assertJsonContract(AttoAmountAsULongSerializer, it, it.raw.toString()) }
    }

    @Test
    fun `should encode heights as decimal strings and accept legacy numbers`() {
        // Given
        val values = listOf(1UL, 2UL, 9_007_199_254_740_992UL, 9_007_199_254_740_993UL, ULong.MAX_VALUE - 1UL, ULong.MAX_VALUE)

        // When
        val heights = values.map { AttoHeight(it) }

        // Then
        heights.forEach { assertJsonContract(AttoHeightSerializer, it, it.value.toString()) }
    }

    @Test
    fun `should encode epoch milliseconds as decimal strings and accept legacy numbers`() {
        // Given
        val values = listOf(Long.MIN_VALUE, -1L, 0L, 1_792_800_000_000L, Long.MAX_VALUE)

        // When
        val instants = values.map { AttoInstant.fromEpochMilliseconds(it) }

        // Then
        instants.forEach { assertJsonContract(AttoInstantAsLongSerializer, it, it.toEpochMilliseconds().toString()) }
    }

    @Test
    fun `should reject invalid amounts at the JSON boundary`() {
        // Given
        val tokens = listOf("-1", "18000000000000000001", "\"18446744073709551616\"", "1.5", "\"invalid\"")

        // When
        val attempts = tokens.map { runCatching { Json.decodeFromString<AttoAmount>(it) } }

        // Then
        attempts.forEach { assertTrue(it.isFailure, "Unexpectedly decoded ${it.getOrNull()}") }
    }

    @Test
    fun `should reject invalid heights at the JSON boundary`() {
        // Given
        val tokens = listOf("-1", "18446744073709551616", "\"18446744073709551616\"", "1.5", "\"invalid\"")

        // When
        val attempts = tokens.map { runCatching { Json.decodeFromString<AttoHeight>(it) } }

        // Then
        attempts.forEach { assertTrue(it.isFailure, "Unexpectedly decoded ${it.getOrNull()}") }
    }

    @Test
    fun `should reject invalid epoch milliseconds at the JSON boundary`() {
        // Given
        val tokens = listOf("9223372036854775808", "\"-9223372036854775809\"", "1.5", "\"invalid\"")

        // When
        val attempts = tokens.map { runCatching { Json.decodeFromString<AttoInstant>(it) } }

        // Then
        attempts.forEach { assertTrue(it.isFailure, "Unexpectedly decoded ${it.getOrNull()}") }
    }

    @Test
    fun `should reject nonnumeric JSON values for numeric domain types`() {
        // Given
        val tokens = listOf("null", "true", "{}", "[]")
        val serializers = listOf(AttoAmountAsULongSerializer, AttoHeightSerializer, AttoInstantAsLongSerializer)

        // When
        val attempts = serializers.flatMap { invalidDecodingAttempts(it, tokens) }

        // Then
        attempts.forEach { assertTrue(it.isFailure, "Unexpectedly decoded ${it.getOrNull()}") }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `should preserve the numeric protobuf contract in both directions`() {
        // Given
        val values =
            listOf(
                RawNumericFields(1UL, 0UL, Long.MIN_VALUE),
                RawNumericFields(9_007_199_254_740_993UL, 9_007_199_254_740_993UL, 0L),
                RawNumericFields(ULong.MAX_VALUE, AttoAmount.MAX.raw, Long.MAX_VALUE),
            )

        // When
        val numericValues =
            values.map {
                NumericFields(AttoHeight(it.height), AttoAmount(it.amount), AttoInstant.fromEpochMilliseconds(it.timestamp))
            }

        // Then
        values.zip(numericValues).forEach { (raw, domain) -> assertProtobufContract(raw, domain) }
    }

    private fun <T> assertJsonContract(
        serializer: KSerializer<T>,
        value: T,
        decimal: String,
    ) {
        // Given
        val quoted = "\"$decimal\""
        val numericElement = Json.parseToJsonElement(decimal)
        val stringElement = JsonPrimitive(decimal)

        // When
        val encoded = Json.encodeToString(serializer, value)
        val encodedElement = Json.encodeToJsonElement(serializer, value)

        // Then
        assertEquals(quoted, encoded)
        assertEquals(stringElement, encodedElement)
        assertEquals(value, Json.decodeFromString(serializer, decimal))
        assertEquals(value, Json.decodeFromString(serializer, quoted))
        assertEquals(value, Json.decodeFromJsonElement(serializer, numericElement))
        assertEquals(value, Json.decodeFromJsonElement(serializer, stringElement))
    }

    private fun <T> invalidDecodingAttempts(
        serializer: KSerializer<T>,
        tokens: List<String>,
    ): List<Result<T>> = tokens.map { runCatching { Json.decodeFromString(serializer, it) } }

    @OptIn(ExperimentalSerializationApi::class)
    private fun assertProtobufContract(
        raw: RawNumericFields,
        domain: NumericFields,
    ) {
        // Given
        val expectedRaw = raw
        val expectedDomain = domain

        // When
        val rawBytes = ProtoBuf.encodeToByteArray(raw)
        val domainBytes = ProtoBuf.encodeToByteArray(domain)

        // Then
        assertContentEquals(rawBytes, domainBytes)
        assertEquals(expectedDomain, ProtoBuf.decodeFromByteArray<NumericFields>(rawBytes))
        assertEquals(expectedRaw, ProtoBuf.decodeFromByteArray<RawNumericFields>(domainBytes))
    }

    @Serializable
    private data class NumericFields(
        val height: AttoHeight,
        val amount: AttoAmount,
        val timestamp: AttoInstant,
    )

    @Serializable
    private data class RawNumericFields(
        val height: ULong,
        val amount: ULong,
        val timestamp: Long,
    )
}
