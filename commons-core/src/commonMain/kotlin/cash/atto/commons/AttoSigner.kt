package cash.atto.commons

interface AttoSigner {
    fun sign(hash: AttoHash): AttoSignature
}

expect class InMemorySigner(privateKey: AttoPrivateKey) : AttoSigner {
    internal val privateKey: AttoPrivateKey
}

fun AttoPrivateKey.toSigner(): AttoSigner {
    return InMemorySigner(this)
}

fun AttoPrivateKey.sign(hash: AttoHash): AttoSignature {
    return this.toSigner().sign(hash)
}
