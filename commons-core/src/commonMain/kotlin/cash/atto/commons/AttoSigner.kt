package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlin.js.JsName

@JsExportForJs
interface AttoSigner {
    companion object {}

    val algorithm: AttoAlgorithm
    val publicKey: AttoPublicKey
    val address: AttoAddress

    @JsName("signHash")
    suspend fun sign(hash: AttoHash): AttoSignature

    @JsName("signBlock")
    suspend fun sign(block: AttoBlock): AttoSignature {
        checkPublicKey(block.publicKey)
        return sign(block.hash)
    }

    @JsName("signVote")
    suspend fun sign(vote: AttoVote): AttoSignature {
        checkPublicKey(vote.publicKey)
        return sign(vote.hash)
    }

    @JsName("signChallenge")
    suspend fun sign(
        challenge: AttoChallenge,
        timestamp: AttoInstant,
    ): AttoSignature = sign(AttoHash.hash(64, publicKey.value, challenge.value, timestamp.toByteArray()))

    @JsName("signMessage")
    suspend fun signMessage(message: ByteArray): AttoSignature = sign(attoSignedMessageHash(publicKey, message))

    suspend fun checkPublicKey(publicKey: AttoPublicKey) {
        if (this.publicKey != publicKey) {
            throw IllegalArgumentException("Different public key ${this.publicKey}")
        }
    }
}

expect class InMemorySigner(
    privateKey: AttoPrivateKey,
) : AttoSigner {
    override val algorithm: AttoAlgorithm
    override val publicKey: AttoPublicKey
    override val address: AttoAddress

    internal val privateKey: AttoPrivateKey

    override suspend fun sign(hash: AttoHash): AttoSignature
}

@JsExportForJs
@JsName("privateKeyToSigner")
fun AttoPrivateKey.toSigner(): AttoSigner = InMemorySigner(this)

fun AttoSeed.toSigner(index: AttoKeyIndex): AttoSigner = toPrivateKey(index).toSigner()

fun AttoSeed.toSigner(index: UInt): AttoSigner = toPrivateKey(index).toSigner()

fun AttoSeed.toSigner(index: Int): AttoSigner = toPrivateKey(index).toSigner()

suspend fun AttoPrivateKey.sign(hash: AttoHash): AttoSignature = this.toSigner().sign(hash)

suspend fun AttoPrivateKey.signMessage(message: ByteArray): AttoSignature = this.toSigner().signMessage(message)
