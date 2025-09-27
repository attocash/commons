package cash.atto.commons

import cash.atto.commons.utils.generateKeyPairFromSeed
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.Promise

@OptIn(ExperimentalJsExport::class)
@JsExport
interface AttoSignerJs {
    val publicKey: AttoPublicKey

    fun signHash(hash: AttoHash): Promise<AttoSignature>

    fun signBlock(block: AttoBlock): Promise<AttoSignature>

    fun signVote(vote: AttoVote): Promise<AttoSignature>

    fun signChallenge(
        challenge: AttoChallenge,
        timestamp: String,
    ): Promise<AttoSignature>

    fun checkPublicKey(publicKey: AttoPublicKey): Promise<Unit>
}

@OptIn(DelicateCoroutinesApi::class) // GlobalScope is marked delicate
private class AttoSignerJsImpl(
    private val signer: AttoSigner,
) : AttoSignerJs {
    override val publicKey: AttoPublicKey = signer.publicKey

    // -------- one-liners that delegate to the suspend API --------

    override fun signHash(hash: AttoHash): Promise<AttoSignature> = GlobalScope.promise { signer.sign(hash) }

    override fun signBlock(block: AttoBlock): Promise<AttoSignature> = GlobalScope.promise { signer.sign(block) }

    override fun signVote(vote: AttoVote): Promise<AttoSignature> = GlobalScope.promise { signer.sign(vote) }

    override fun signChallenge(
        challenge: AttoChallenge,
        timestamp: String,
    ): Promise<AttoSignature> = GlobalScope.promise { signer.sign(challenge, AttoInstant.fromIso(timestamp)) }

    override fun checkPublicKey(publicKey: AttoPublicKey): Promise<Unit> = GlobalScope.promise { signer.checkPublicKey(publicKey) }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun AttoPrivateKey.toSignerJs(): AttoSignerJs {
    val signer = toSigner()
    return AttoSignerJsImpl(signer)
}

actual class InMemorySigner actual constructor(
    internal actual val privateKey: AttoPrivateKey,
) : AttoSigner {
    actual override val publicKey: AttoPublicKey = privateKey.toPublicKey()

    private val keyPair = generateKeyPairFromSeed(privateKey.value.toUint8Array())

    actual override suspend fun sign(hash: AttoHash): AttoSignature {
        val signature =
            cash.atto.commons.utils.sign(
                secretKey = keyPair.secretKey,
                message = hash.value.toUint8Array(),
            )

        return AttoSignature(signature.toByteArray())
    }
}
