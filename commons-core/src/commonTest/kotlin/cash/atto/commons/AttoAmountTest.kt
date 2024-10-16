package cash.atto.commons

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `should convert to atto`() {
        // given
        val amount = AttoAmount.MAX

        // when
        val string = amount.toString(AttoUnit.ATTO)

        // then
        assertEquals("18000000000", string)

    }
}
