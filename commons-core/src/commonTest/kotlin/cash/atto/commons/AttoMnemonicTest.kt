package cash.atto.commons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

internal class AttoMnemonicTest {
    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `should create mnemonic`() {
        // given
        val expectedMnemonic =
            AttoMnemonic(
                "edge defense waste choose enrich upon flee junk siren film clown finish luggage leader kid quick brick print evidence swap drill paddle truly occur",
            )

        // when
        val entropy = expectedMnemonic.toEntropy()
        val mnemonic = AttoMnemonic(entropy)

        // then
        assertEquals(expectedMnemonic.words.joinToString(" "), mnemonic.words.joinToString(" "))
    }

    @Test
    fun `should generate mnemonic`() {
        AttoMnemonic.generate()
    }

    @Test
    fun `should throw exception when mnemonic has invalid size`() {
        // when
        val exception = assertFails { AttoMnemonic("edge") }

        // then
        assertTrue(exception is AttoMnemonicException)
    }

    @Test
    fun `should throw exception when mnemonic has invalid word`() {
        // given
        val words = AttoMnemonic.generate().words.toMutableList()
        words[0] = "atto"

        // when
        val exception = assertFails { AttoMnemonic(words) }

        // then
        assertTrue(exception is AttoMnemonicException)
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `should throw exception when mnemonic has invalid checksum`() {
        // given
        val words =
            "edge defense waste choose enrich upon flee junk siren film clown finish luggage leader kid quick brick print evidence swap drill paddle truly truly"

        // when
        val exception = assertFails { AttoMnemonic(words) }

        // then
        assertTrue(exception is AttoMnemonicException)
    }

    @Test
    fun `should return dictionary`() {
        assertEquals(2048, AttoMnemonicDictionary.list.size)
    }
}
