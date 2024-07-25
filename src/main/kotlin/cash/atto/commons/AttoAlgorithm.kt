package cash.atto.commons

import kotlinx.serialization.Serializable

@Serializable
enum class AttoAlgorithm(
    val code: UByte,
    val privateKeySize: Int,
    val publicKeySize: Int,
    val hashSize: Int,
) {
    UNKNOWN(UByte.MAX_VALUE, 0, 0, 0),
    V1(0u, 32, 32, 32), // BLAKE2B + ED25519
    ;

    companion object {
        private val map = AttoAlgorithm.entries.associateBy(AttoAlgorithm::code)

        fun from(code: UByte): AttoAlgorithm = map.getOrDefault(code, UNKNOWN)
    }
}
