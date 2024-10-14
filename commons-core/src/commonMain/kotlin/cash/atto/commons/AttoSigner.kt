package cash.atto.commons

interface AttoSigner {
    val publicKey: AttoPublicKey
    fun sign(hash: AttoHash): AttoSignature
}

expect class InMemorySigner(privateKey: AttoPrivateKey) : AttoSigner {
    override val publicKey: AttoPublicKey
    internal val privateKey: AttoPrivateKey
}

fun AttoPrivateKey.toSigner(): AttoSigner {
    return InMemorySigner(this)
}

fun AttoPrivateKey.sign(hash: AttoHash): AttoSignature {
    return this.toSigner().sign(hash)
}
