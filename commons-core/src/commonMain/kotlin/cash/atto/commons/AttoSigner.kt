package cash.atto.commons

interface AttoSigner {
    val publicKey: AttoPublicKey

    suspend fun sign(hash: AttoHash): AttoSignature

    suspend fun sign(hashable: AttoHashable): AttoSignature {
        return sign(hashable.hash)
    }

    suspend fun sign(challenge: AttoChallenge): AttoSignature {
        return sign(AttoHash.hash(64, publicKey.value, challenge.value))
    }
}

expect class InMemorySigner(privateKey: AttoPrivateKey) : AttoSigner {
    override val publicKey: AttoPublicKey
    internal val privateKey: AttoPrivateKey
}

fun AttoPrivateKey.toSigner(): AttoSigner {
    return InMemorySigner(this)
}

suspend fun AttoPrivateKey.sign(hash: AttoHash): AttoSignature {
    return this.toSigner().sign(hash)
}
