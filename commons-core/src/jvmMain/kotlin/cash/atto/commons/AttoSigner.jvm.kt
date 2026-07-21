@file:JvmName("AttoSigners")

package cash.atto.commons

import kotlinx.coroutines.runBlocking
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import kotlin.jvm.JvmName

internal actual class Ed25519SigningKey(
    privateKey: AttoPrivateKey,
) {
    private val parameters = Ed25519PrivateKeyParameters(privateKey.value, 0)

    actual val publicKey: AttoPublicKey = AttoPublicKey(parameters.generatePublicKey().encoded)

    actual suspend fun sign(hash: AttoHash): AttoSignature {
        val signer = Ed25519Signer()
        signer.init(true, parameters)
        signer.update(hash.value, 0, hash.value.size)
        return AttoSignature(signer.generateSignature())
    }
}

internal actual suspend fun loadEd25519SigningKey(privateKey: AttoPrivateKey): Ed25519SigningKey = Ed25519SigningKey(privateKey)

fun AttoPrivateKey.toSignerBlocking(): AttoSigner = runBlocking { toSigner() }

fun AttoSeed.toSignerBlocking(index: AttoKeyIndex): AttoSigner = runBlocking { toSigner(index) }

fun AttoSeed.toSignerBlocking(index: UInt): AttoSigner = runBlocking { toSigner(index) }

fun AttoSeed.toSignerBlocking(index: Int): AttoSigner = runBlocking { toSigner(index) }

fun AttoPrivateKey.signBlocking(hash: AttoHash): AttoSignature = runBlocking { sign(hash) }

fun AttoPrivateKey.signMessageBlocking(message: ByteArray): AttoSignature = runBlocking { signMessage(message) }

fun AttoSigner.signBlocking(hash: AttoHash): AttoSignature =
    runBlocking {
        sign(hash)
    }

fun AttoSigner.signBlocking(block: AttoBlock): AttoSignature =
    runBlocking {
        sign(block)
    }

fun AttoSigner.signBlocking(vote: AttoVote): AttoSignature = runBlocking { sign(vote) }

fun AttoSigner.signBlocking(
    challenge: AttoChallenge,
    timestamp: AttoInstant,
): AttoSignature = runBlocking { sign(challenge, timestamp) }

fun AttoSigner.signMessageBlocking(message: ByteArray): AttoSignature = runBlocking { signMessage(message) }

fun AttoSigner.checkPublicKeyBlocking(publicKey: AttoPublicKey) {
    runBlocking { checkPublicKey(publicKey) }
}
