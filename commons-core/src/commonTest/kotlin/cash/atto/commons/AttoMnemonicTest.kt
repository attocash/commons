package cash.atto.commons

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class AttoMnemonicTest {
    @Test
    fun `should encode published BIP39 entropy vectors`() =
        runTest {
            for (vector in mnemonicVectors) {
                // Given
                val entropy = vector.entropyHex.fromHexToByteArray()
                val originalEntropy = entropy.copyOf()

                // When
                val mnemonic = AttoMnemonic.fromEntropy(entropy)

                // Then
                assertEquals(vector.phrase, mnemonic.phrase, vector.entropyHex)
                assertContentEquals(originalEntropy, entropy, "Source entropy must not be mutated")
            }
        }

    @Test
    fun `should decode published BIP39 phrases to raw entropy`() =
        runTest {
            for (vector in mnemonicVectors) {
                // Given
                val expectedEntropy = vector.entropyHex.fromHexToByteArray()
                val mnemonic = AttoMnemonic.fromPhrase(vector.phrase)

                // When
                val entropy = mnemonic.toEntropy()

                // Then
                assertEquals(32, entropy.size, vector.entropyHex)
                assertContentEquals(expectedEntropy, entropy, vector.entropyHex)
            }
        }

    @Test
    fun `should round trip published BIP39 entropy and phrases`() =
        runTest {
            for (vector in mnemonicVectors) {
                // Given
                val entropy = vector.entropyHex.fromHexToByteArray()
                val mnemonic = AttoMnemonic.fromPhrase(vector.phrase)

                // When
                val restoredEntropy = AttoMnemonic.fromEntropy(entropy).toEntropy()
                val restoredMnemonic = AttoMnemonic.fromEntropy(mnemonic.toEntropy())

                // Then
                assertContentEquals(entropy, restoredEntropy, vector.entropyHex)
                assertEquals(mnemonic, restoredMnemonic, vector.entropyHex)
            }
        }

    @Test
    fun `should generate mnemonic`() =
        runTest {
            // Given
            val expectedWordCount = 24
            val expectedEntropySize = 32

            // When
            val mnemonic = AttoMnemonic.generate()
            val entropy = mnemonic.toEntropy()
            val parsedMnemonic = AttoMnemonic.fromPhrase(mnemonic.phrase)
            val restoredMnemonic = AttoMnemonic.fromEntropy(entropy)

            // Then
            assertEquals(expectedWordCount, mnemonic.words.size)
            assertTrue(mnemonic.words.all { it in AttoMnemonicDictionary.set })
            assertEquals(expectedEntropySize, entropy.size)
            assertEquals(mnemonic, parsedMnemonic)
            assertEquals(mnemonic, restoredMnemonic)
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
            // Given
            val unsupportedSizes = listOf(0, 16, 20, 24, 28, 31, 33, 34, 35, 64)

            // When / Then
            for (size in unsupportedSizes) {
                assertFailsWith<AttoMnemonicException>("Entropy size $size must be rejected") {
                    AttoMnemonic.fromEntropy(ByteArray(size))
                }
            }
        }

    @Test
    fun `should return dictionary`() {
        assertEquals(2048, AttoMnemonicDictionary.list.size)
    }

    private data class MnemonicVector(
        val entropyHex: String,
        val phrase: String,
    )

    private companion object {
        // Published 256-bit English vectors: https://github.com/trezor/python-mnemonic/blob/master/vectors.json
        val mnemonicVectors =
            listOf(
                MnemonicVector(
                    "00".repeat(32),
                    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art",
                ),
                MnemonicVector(
                    "7f".repeat(32),
                    "legal winner thank year wave sausage worth useful legal winner thank year " +
                        "wave sausage worth useful legal winner thank year wave sausage worth title",
                ),
                MnemonicVector(
                    "80".repeat(32),
                    "letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd " +
                        "amount doctor acoustic avoid letter advice cage absurd amount doctor acoustic bless",
                ),
                MnemonicVector(
                    "ff".repeat(32),
                    "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo vote",
                ),
                MnemonicVector(
                    "68a79eaca2324873eacc50cb9c6eca8cc68ea5d936f98787c60c7ebc74e6ce7c",
                    "hamster diagram private dutch cause delay private meat slide toddler razor book " +
                        "happy fancy gospel tennis maple dilemma loan word shrug inflict delay length",
                ),
            )
    }
}
