package cash.atto.commons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class AttoBlockingCryptoTest {
    @Test
    fun `should expose blocking crypto bridges`() {
        // given
        val mnemonic =
            mnemonicFromPhraseBlocking(
                "edge defense waste choose enrich upon flee junk siren film clown finish luggage leader kid quick brick print evidence swap drill paddle truly occur",
            )
        val seed = mnemonic.toSeedBlocking()

        // when
        val privateKey = seed.toPrivateKeyBlocking(0U)
        val publicKey = privateKey.toPublicKeyBlocking()
        val signer = privateKey.toSignerBlocking()
        val hash = AttoHash.hash(32, "atto".encodeToByteArray())
        val signature = signer.signBlocking(hash)

        // then
        assertEquals(publicKey, signer.publicKey)
        assertTrue(signature.isValidBlocking(publicKey, hash))
    }

    @Test
    fun `should not expose completable future crypto bridges`() {
        // given
        val facadeTypes =
            listOf(
                "AttoMnemonics",
                "AttoSeeds",
                "AttoPrivateKeys",
                "AttoPublicKeys",
                "AttoSigners",
                "AttoSignatures",
                "AttoTransactions",
                "AttoVotes",
            ).map { Class.forName("cash.atto.commons.$it") }

        // when
        val asyncMethods =
            facadeTypes
                .flatMap { it.declaredMethods.toList() }
                .filter { it.name.endsWith("Async") }

        // then
        assertFalse(asyncMethods.isNotEmpty(), asyncMethods.joinToString { it.toGenericString() })
    }

    @Test
    fun `should hide suspend methods from Java`() {
        // given
        val publicTypes =
            listOf(
                AttoMnemonic::class.java,
                AttoSeed::class.java,
                AttoPrivateKey::class.java,
                AttoSigner::class.java,
                AttoSignature::class.java,
                AttoSignedVote::class.java,
                AttoTransaction::class.java,
            )

        // when
        val visibleContinuationMethods =
            publicTypes
                .flatMap { it.declaredMethods.toList() }
                .filter { method ->
                    method.parameterTypes.lastOrNull()?.name == "kotlin.coroutines.Continuation" && !method.isSynthetic
                }

        // then
        assertFalse(visibleContinuationMethods.isNotEmpty(), visibleContinuationMethods.joinToString { it.toGenericString() })
    }
}
