package cash.atto.commons.compatibility

import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoMnemonic
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import cash.atto.commons.isValid as legacyIsValid
import cash.atto.commons.sign as legacySign
import cash.atto.commons.toAddress as legacyToAddress
import cash.atto.commons.toPrivateKey as legacyToPrivateKey
import cash.atto.commons.toPublicKey as legacyToPublicKey
import cash.atto.commons.toSeed as legacyToSeed
import cash.atto.commons.toSigner as legacyToSigner

@Suppress("DEPRECATION")
internal class LegacyExtensionCompatibilityTest {
    @Test
    fun `should delegate legacy crypto extensions to members`() =
        runTest {
            // given
            val mnemonic =
                AttoMnemonic.fromPhrase(
                    "edge defense waste choose enrich upon flee junk siren film clown finish luggage leader kid quick brick print evidence swap drill paddle truly occur",
                )
            val hash = AttoHash.hash(32, "atto".encodeToByteArray())

            // when
            val seed = mnemonic.legacyToSeed()
            val privateKey = seed.legacyToPrivateKey(0U)
            val publicKey = privateKey.legacyToPublicKey()
            val signer = privateKey.legacyToSigner()
            val signature = privateKey.legacySign(hash)

            // then
            assertEquals(publicKey.legacyToAddress(AttoAlgorithm.V1), signer.address)
            assertTrue(signature.legacyIsValid(publicKey, hash))
        }
}
