@file:JvmName("AttoSignatures")

package cash.atto.commons

import kotlinx.coroutines.runBlocking
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import kotlin.jvm.JvmName

internal actual suspend fun verifyEd25519(
    signature: AttoSignature,
    publicKey: AttoPublicKey,
    hash: AttoHash,
): Boolean {
    val parameters = Ed25519PublicKeyParameters(publicKey.value, 0)
    val signer = Ed25519Signer()
    signer.init(false, parameters)
    signer.update(hash.value, 0, hash.value.size)
    return signer.verifySignature(signature.value)
}

fun AttoSignature.isValidBlocking(
    publicKey: AttoPublicKey,
    hash: AttoHash,
): Boolean = runBlocking { isValid(publicKey, hash) }

fun AttoSignature.isValidBlocking(
    publicKey: AttoPublicKey,
    challenge: AttoChallenge,
    timestamp: AttoInstant,
): Boolean = runBlocking { isValid(publicKey, challenge, timestamp) }

fun AttoSignature.isValidMessageBlocking(
    publicKey: AttoPublicKey,
    message: ByteArray,
): Boolean = runBlocking { isValidMessage(publicKey, message) }
