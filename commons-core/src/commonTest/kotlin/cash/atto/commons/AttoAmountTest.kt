package cash.atto.commons

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class AttoAmountTest {
    @Test
    fun sum() {
        // given
        val amount1 = AttoAmount(1u)

        // when
        val total = amount1 + amount1

        // then
        assertEquals(AttoAmount(2u), total)
    }

    @Test
    fun subtract() {
        // given
        val amount3 = AttoAmount(3u)
        val amount1 = AttoAmount(1u)

        // when
        val total = amount3 - amount1

        // then
        assertEquals(AttoAmount(2u), total)
    }

    @Test
    fun parseDecimal() {
        listOf("1.0321", "0.01", "1", "1.23").forEach { stringAmount ->
            // when
            val amount = AttoAmount.from(AttoUnit.ATTO, stringAmount)

            // then
            assertEquals(stringAmount, amount.toString(AttoUnit.ATTO))
        }
    }

    @Test
    fun overflow() {
        try {
            AttoAmount.MAX + AttoAmount.MAX
        } catch (e: IllegalStateException) {
            assertEquals("ULong overflow", e.message)
        }
    }

    @Test
    fun underflow() {
        try {
            AttoAmount.MIN - AttoAmount.MAX
        } catch (e: IllegalStateException) {
            assertEquals("ULong underflow", e.message)
        }
    }

    @Test
    fun aboveMaxAmount() {
        try {
            AttoAmount.MAX + AttoAmount(1U)
        } catch (e: IllegalStateException) {
            assertEquals("18000000000000000001 exceeds the max amount of 18000000000000000000", e.message)
        }
    }

    @Test
    fun `should serialize json`() {
        // given
        val expectedJson = "18000000000000000000"

        // when
        val amount = Json.decodeFromString<AttoAmount>(expectedJson)
        val json = Json.encodeToString(amount)

        // then
        assertEquals(expectedJson, json)
        assertEquals(AttoAmount.MAX, amount)
    }

    @Test
    fun `should convert from atto unit to string`() {
        // given
        val amount = AttoAmount.MAX

        // when
        val string = amount.toString(AttoUnit.ATTO)

        // then
        assertEquals("18000000000", string)
    }

    @Test
    fun `should convert from string to atto unit`() {
        // given
        val string = "1"

        // when
        val amount = AttoAmount.from(AttoUnit.ATTO, "1")

        // then
        assertEquals(AttoAmount(1_000_000_000UL), amount)
    }

    @Test
    fun `should reject malformed decimal strings`() {
        listOf("", ".", ".1", "1.", "1.2.3", " 1", "+1", "-1", "1.0000000000").forEach { string ->
            assertFailsWith<IllegalArgumentException>(string) {
                AttoAmount.from(AttoUnit.ATTO, string)
            }
        }

        assertFailsWith<IllegalArgumentException> {
            AttoAmount.from(AttoUnit.RAW, "1.0")
        }
    }

    @Test
    fun `should reject overflow before constructing amount`() {
        assertEquals(AttoAmount.MAX, AttoAmount.from(AttoUnit.ATTO, "18000000000"))

        listOf("18000000000.000000001", "18000000001", "18446744074").forEach { string ->
            assertFailsWith<IllegalArgumentException>(string) {
                AttoAmount.from(AttoUnit.ATTO, string)
            }
        }
    }
}
