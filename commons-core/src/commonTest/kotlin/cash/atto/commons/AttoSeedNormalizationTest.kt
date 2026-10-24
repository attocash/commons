package cash.atto.commons

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class AttoSeedNormalizationTest {
    @Test
    fun `should derive the official 24 word BIP39 TREZOR seed`() =
        runTest {
            // Given
            // https://github.com/trezor/python-mnemonic/blob/master/vectors.json (256-bit zero entropy).
            val mnemonic = AttoMnemonic.fromPhrase(BIP39_MNEMONIC_PHRASE)

            // When
            val seed = mnemonic.toSeed("TREZOR")

            // Then
            assertEquals(OFFICIAL_TREZOR_SEED, seed.value.toHex())
        }

    @Test
    fun `should retain the default and explicit empty passphrase seed`() =
        runTest {
            // Given
            val mnemonic = AttoMnemonic.fromPhrase(EXISTING_MNEMONIC_PHRASE)
            val expectedSeed =
                "7E74B1A8195AE1E8D06F29C9A306F678E5A8CF908075BC52EB3B716F9E50CE88" +
                    "60065C2C18B8A960BB363855D3A340074CBA5DB505D4F78DD1D94C4E19F20B7A"

            // When
            val defaultSeed = mnemonic.toSeed()
            val explicitEmptySeed = mnemonic.toSeed("")

            // Then
            assertEquals(expectedSeed, defaultSeed.value.toHex())
            assertEquals(expectedSeed, explicitEmptySeed.value.toHex())
        }

    @Test
    fun `should retain the ASCII vector and preserve passphrase case and whitespace`() =
        runTest {
            // Given
            val mnemonic = AttoMnemonic.fromPhrase(EXISTING_MNEMONIC_PHRASE)
            val expectedSeed =
                "0DC285FDE768F7FF29B66CE7252D56ED92FE003B605907F7A4F683C3DC8586D3" +
                    "4A914D3C71FC099BB38EE4A59E5B081A3497B7A323E90CC68F67B5837690310C"

            // When
            val seed = mnemonic.toSeed("some password")
            val differentCaseSeed = mnemonic.toSeed("Some password")
            val surroundingWhitespaceSeed = mnemonic.toSeed(" some password ")

            // Then
            assertEquals(expectedSeed, seed.value.toHex())
            assertFalse(seed.value.contentEquals(differentCaseSeed.value))
            assertFalse(seed.value.contentEquals(surroundingWhitespaceSeed.value))
        }

    @Test
    fun `should derive the same seed for composed and decomposed passphrases`() =
        runTest {
            // Given
            val mnemonic = AttoMnemonic.fromPhrase(BIP39_MNEMONIC_PHRASE)
            val expectedSeed =
                "5473DB1E091268961306ABC6EC40FA37DDE0A3941BFCD1DC51EC8DBA85B1808F" +
                    "677044CBD09379624B76078694A6EC1D665EDD442911D5E054FCA1495234A4A1"

            // When
            val composedSeed = mnemonic.toSeed("caf\u00E9")
            val decomposedSeed = mnemonic.toSeed("cafe\u0301")

            // Then
            assertEquals(expectedSeed, composedSeed.value.toHex())
            assertEquals(expectedSeed, decomposedSeed.value.toHex())
        }

    @Test
    fun `should apply compatibility decomposition to passphrases`() =
        runTest {
            // Given
            val mnemonic = AttoMnemonic.fromPhrase(BIP39_MNEMONIC_PHRASE)
            val fullwidthPassphrase = "\uFF34\uFF32\uFF25\uFF3A\uFF2F\uFF32"

            // When
            val seed = mnemonic.toSeed(fullwidthPassphrase)

            // Then
            assertEquals(OFFICIAL_TREZOR_SEED, seed.value.toHex())
        }

    @Test
    fun `should normalize the official Japanese passphrase with an English mnemonic`() =
        runTest {
            // Given
            // https://github.com/bip32JP/bip32JP.github.io/blob/master/test_JP_BIP39.json
            // Reuse its normalization stress passphrase with the supported English wordlist.
            val mnemonic = AttoMnemonic.fromPhrase(BIP39_MNEMONIC_PHRASE)
            val expectedSeed =
                "0BDD957C43B3BD28D4199CDFF730243C482113AB0562110192FAFE931716AC2B" +
                    "3E1C9EA4120CEF416D61281FAE975DE28DF23F80D44F3CE39D2583151B4B9B72"

            // When
            val seed = mnemonic.toSeed("㍍ガバヴァぱばぐゞちぢ十人十色")

            // Then
            assertEquals(expectedSeed, seed.value.toHex())
        }

    @Test
    fun `should normalize supplementary and compatibility characters in mnemonic text`() {
        // Given
        // U+1D41A is MATHEMATICAL BOLD SMALL A and occupies a UTF-16 surrogate pair.
        val compatibilityPhrase =
            BIP39_MNEMONIC_PHRASE.replaceFirst("abandon ", "\uD835\uDC1Aｂａｎｄｏｎ\u3000")

        // When
        val normalizedPhrase = normalizeMnemonicNfkd(compatibilityPhrase)

        // Then
        assertEquals(BIP39_MNEMONIC_PHRASE, normalizedPhrase)
    }

    private companion object {
        const val BIP39_MNEMONIC_PHRASE =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        const val EXISTING_MNEMONIC_PHRASE =
            "edge defense waste choose enrich upon flee junk siren film clown finish " +
                "luggage leader kid quick brick print evidence swap drill paddle truly occur"
        const val OFFICIAL_TREZOR_SEED =
            "BDA85446C68413707090A52022EDD26A1C9462295029F2E60CD7C4F2BBD30971" +
                "70AF7A4D73245CAFA9C3CCA8D561A7C3DE6F5D4A10BE8ED2A5E608D68F92FCC8"
    }
}
