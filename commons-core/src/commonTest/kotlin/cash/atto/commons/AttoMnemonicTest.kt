package cash.atto.commons

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class AttoMnemonicTest {
    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `should create mnemonic`() =
        runTest {
            // given
            val expectedMnemonic =
                AttoMnemonic.fromPhrase(
                    "edge defense waste choose enrich upon flee junk siren film clown finish luggage leader kid quick brick print evidence swap drill paddle truly occur",
                )

            // when
            val entropy = expectedMnemonic.toEntropy()
            val mnemonic = AttoMnemonic.fromEntropy(entropy)

            // then
            assertEquals(expectedMnemonic.words.joinToString(" "), mnemonic.words.joinToString(" "))
        }

    @Test
    fun `should generate mnemonic`() =
        runTest {
            AttoMnemonic.generate()
        }

    @Test
    fun `should throw exception when mnemonic has invalid size`() =
        runTest {
            assertFailsWith<AttoMnemonicException> { AttoMnemonic.fromPhrase("edge") }
        }

    @Test
    fun `should throw exception when mnemonic has invalid word`() =
        runTest {
            // given
            val words = AttoMnemonic.generate().words.toMutableList()
            words[0] = "atto"

            // when / then
            assertFailsWith<AttoMnemonicException> { AttoMnemonic.fromWords(words) }
        }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `should throw exception when mnemonic has invalid checksum`() =
        runTest {
            // given
            val words =
                "edge defense waste choose enrich upon flee junk siren film clown finish luggage leader kid quick brick print evidence swap drill paddle truly truly"

            // when / then
            assertFailsWith<AttoMnemonicException> { AttoMnemonic.fromPhrase(words) }
        }

    @Test
    fun `should reject invalid entropy sizes`() =
        runTest {
            listOf(32, 34, 35).forEach { size ->
                assertFailsWith<AttoMnemonicException> { AttoMnemonic.fromEntropy(ByteArray(size)) }
            }
        }

    @Test
    fun `should return dictionary`() {
        assertEquals(2048, AttoMnemonicDictionary.list.size)
    }
}
