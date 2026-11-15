package cash.atto.commons.spring

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException

class AttoStreamRequestTargetTest {
    @Test
    fun `target decodes each segment once and preserves repeated query values`() {
        // Given
        val path = "/v2/accounts/atto%3A%2F%2Fencoded%252Faddress/stream?names=a%2Cb&names=c+d"

        // When
        val target = AttoStreamRequestTarget(path)

        // Then
        assertEquals(listOf("v2", "accounts", "atto://encoded%2Faddress", "stream"), target.segments)
        assertEquals(listOf("a,b", "c d"), target.queryValues("names"))
    }

    @Test
    fun `target rejects absolute authority fragment and malformed escaping`() {
        // Given
        val paths = listOf("https://example.test/v2/accounts/stream", "//example.test/v2/accounts/stream", "/v2/stream#fragment", "/v2/%xx")

        // When / Then
        paths.forEach { path ->
            assertEquals(400, assertThrows(ResponseStatusException::class.java) { AttoStreamRequestTarget(path) }.statusCode.value())
        }
    }
}
