@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.worker

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.Test
import kotlin.test.assertEquals

class WebGLWorkResultTest {
    @Test
    fun `should report no winner when every found flag is zero`() {
        // Given
        val pixels = pixelsWithoutWinner()

        // When
        val result = readWebGLWorkResult(pixels)

        // Then
        assertEquals(WebGLWorkResult(0, 0, false), result)
    }

    @Test
    fun `should read winner at first pixel`() {
        // Given
        val pixels = pixelsWithFirstWinner()

        // When
        val result = readWebGLWorkResult(pixels)

        // Then
        assertEquals(WebGLWorkResult(11, 12, true), result)
    }

    @Test
    fun `should read winner at final pixel of a full batch`() {
        // Given
        val pixels = pixelsWithFinalWinner()

        // When
        val result = readWebGLWorkResult(pixels)

        // Then
        assertEquals(WebGLWorkResult(21, 22, true), result)
    }

    @Test
    fun `should preserve high bits in nonce words`() {
        // Given
        val pixels = pixelsWithHighBitNonce()

        // When
        val result = readWebGLWorkResult(pixels)

        // Then
        assertEquals(WebGLWorkResult(Int.MIN_VALUE, -1, true), result)
    }

    @Test
    fun `should return first winner when multiple pixels have nonzero found flags`() {
        // Given
        val pixels = pixelsWithMultipleWinners()

        // When
        val result = readWebGLWorkResult(pixels)

        // Then
        assertEquals(WebGLWorkResult(31, 32, true), result)
    }
}

private fun pixelsWithoutWinner(): Uint32Array = js("new Uint32Array([1, 2, 0, 3, 4, 5, 0, 6])")

private fun pixelsWithFirstWinner(): Uint32Array = js("new Uint32Array([11, 12, 1, 0, 0, 0, 0, 0])")

private fun pixelsWithFinalWinner(): Uint32Array =
    js(
        """
        (() => {
            const pixels = new Uint32Array(256 * 256 * 4);
            const offset = pixels.length - 4;
            pixels[offset] = 21;
            pixels[offset + 1] = 22;
            pixels[offset + 2] = 1;
            return pixels;
        })()
        """,
    )

private fun pixelsWithHighBitNonce(): Uint32Array = js("new Uint32Array([0x80000000, 0xffffffff, 1, 0])")

private fun pixelsWithMultipleWinners(): Uint32Array = js("new Uint32Array([0, 0, 0, 0, 31, 32, 0x80000000, 0, 41, 42, 1, 0])")
