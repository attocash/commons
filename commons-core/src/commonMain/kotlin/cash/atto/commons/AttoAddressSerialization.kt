package cash.atto.commons

internal fun resolveSerializedAddress(
    field: String,
    address: AttoAddress?,
    algorithm: AttoAlgorithm?,
    publicKey: AttoPublicKey?,
): AttoAddress {
    if (address != null) {
        return address
    }

    require(algorithm != null && publicKey != null) { "$field requires an address or both algorithm and publicKey" }
    return AttoAddress(algorithm, publicKey)
}
