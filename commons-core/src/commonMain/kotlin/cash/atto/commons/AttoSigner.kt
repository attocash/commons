package cash.atto.commons

import kotlinx.datetime.Instant

interface AttoSigner {
    companion object {}

    val publicKey: AttoPublicKey

    suspend fun sign(hash: AttoHash): AttoSignature

    suspend fun sign(block: AttoBlock): AttoSignature {
        checkPublicKey(block.publicKey)
        return sign(block.hash)
    }

    suspend fun sign(vote: AttoVote): AttoSignature {
        checkPublicKey(vote.publicKey)
        return sign(vote.hash)
    }

    suspend fun sign(challenge: AttoChallenge, timestamp: Instant): AttoSignature {
        return sign(AttoHash.hash(64, publicKey.value, challenge.value, timestamp.toByteArray()))
    }

    suspend fun checkPublicKey(publicKey: AttoPublicKey) {
        if (this.publicKey != publicKey) {
            throw IllegalArgumentException("Different public key ${this.publicKey}")
        }
    }
}

expect class InMemorySigner(privateKey: AttoPrivateKey) : AttoSigner {
    override val publicKey: AttoPublicKey
    internal val privateKey: AttoPrivateKey

    override suspend fun sign(hash: AttoHash): AttoSignature
}

fun AttoPrivateKey.toSigner(): AttoSigner {
    return InMemorySigner(this)
}

suspend fun AttoPrivateKey.sign(hash: AttoHash): AttoSignature {
    return this.toSigner().sign(hash)
}
