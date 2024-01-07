@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import cash.atto.commons.serialiazers.json.AttoJson
import cash.atto.commons.serialiazers.protobuf.AttoProtobuf
import kotlinx.serialization.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
        val amount = AttoJson.decodeFromString<AttoAmount>(expectedJson)
        val json = AttoJson.encodeToString(amount)

        // then
        assertEquals(expectedJson, json)
        assertEquals(AttoAmount.MAX, amount)
    }

    @Test
    fun `should serialize protobuf`() {
        // given
        val expectedProtobuf = "088080A0A89C94B6E6F901"

        // when
        val holder = AttoProtobuf.decodeFromHexString<Holder>(expectedProtobuf)
        val protobuf = AttoProtobuf.encodeToHexString(holder).uppercase()

        // then
        assertEquals(expectedProtobuf, protobuf)
        assertEquals(AttoAmount.MAX, holder.amount)
    }

    @Serializable
    private data class Holder(val amount: AttoAmount)
}