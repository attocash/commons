package cash.atto.commons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


internal class AttoAddressTest {
    private val expectedAddress =
        AttoAddress.parse("atto://aarfzz26z5pfwrkdcwt4jdhhe2vvixscqwehgmfjqxku43rgtjso5p5cjw6fw")

    @Test
    fun `should create account`() {
        // given
        val publicKey = expectedAddress.publicKey

        // when
        val account = publicKey.toAddress(expectedAddress.algorithm)

        // then
        assertEquals(expectedAddress, account)
    }

    @Test
    fun `should extract address to public key`() {
        // when
        val publicKey = expectedAddress.publicKey

        // then
        assertEquals(expectedAddress, publicKey.toAddress(AttoAlgorithm.V1))
    }

    @Test
    fun `should parse address`() {
        // when
        val address = AttoAddress.parse(expectedAddress.value)

        // then
        assertEquals(expectedAddress, address)
    }

    @Test
    fun `should parse path`() {
        // when
        val address = AttoAddress.parsePath(expectedAddress.path)

        // then
        assertEquals(expectedAddress, address)
    }

    @Test
    fun `should throw illegal argument exception when regex doesn't match`() {
        // given
        val wrongAccount = expectedAddress.value.replace("atto:", "nano_")

        // when
        val exception = assertFails { AttoAddress.parse(wrongAccount) }

        // then
        assertTrue(exception is IllegalArgumentException)
    }

    @Test
    fun `should throw illegal argument exception when checksum doesn't match`() {
        // given
        val wrongAccount = expectedAddress.value.substring(0, expectedAddress.value.length - 1) + "a"

        // when
        val exception = assertFails { AttoAddress.parse(wrongAccount) }

        // then
        assertTrue(exception is IllegalArgumentException)
    }
}
