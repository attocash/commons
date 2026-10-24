package cash.atto.commons

import kotlin.test.Test
import kotlin.test.assertEquals

class AttoHeightExportTest {
    private val heights =
        listOf(
            "1",
            "9007199254740991",
            "9007199254740993",
            "9223372036854775807",
            "9223372036854775808",
            "18446744073709551615",
        )

    @Test
    fun `should convert heights to JavaScript bigint without precision or sign loss`() {
        // Given
        for (expected in heights) {
            val height = expected.toAttoHeight()

            // When
            val actual = javascriptBigIntString(height)

            // Then
            assertEquals(expected, actual)
            assertEquals(expected.toULong(), height.value)
        }
    }

    @Test
    fun `should not expose the signed backing value to JavaScript`() {
        // Given
        val height = AttoHeight.MAX

        // When
        val exportedValueType = javascriptValueType(height)

        // Then
        assertEquals("undefined", exportedValueType)
    }

    private fun javascriptBigIntString(height: AttoHeight): String = js("BigInt(height.toString()).toString()")

    private fun javascriptValueType(height: AttoHeight): String = js("typeof height.value")
}
