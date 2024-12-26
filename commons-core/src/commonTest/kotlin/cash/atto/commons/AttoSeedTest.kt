package cash.atto.commons

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue


internal class AttoSeedTest {
    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `should crete seed from mnemonic`() = runTest {
        // given
        val mnemonic =
            AttoMnemonic(
                "edge defense waste choose enrich upon flee junk siren film clown finish luggage leader kid quick brick print evidence swap drill paddle truly occur",
            )

        // when
        val seed = mnemonic.toSeed("some password")

        // then
        assertEquals(
            "0DC285FDE768F7FF29B66CE7252D56ED92FE003B605907F7A4F683C3DC8586D34A914D3C71FC099BB38EE4A59E5B081A3497B7A323E90CC68F67B5837690310C",
            seed.value.toHex(),
        )
    }

    @Test
    fun `should not instantiate invalid seed`() {
        // when
        val exception = assertFails { AttoSeed(ByteArray(31)) }

        // then
        assertTrue(exception is IllegalArgumentException)
    }
}
