@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class AttoAlgorithm(
    val code: UByte,
    val privateKeySize: Int,
    val publicKeySize: Int,
    val hashSize: Int,
) {
    @ProtoNumber(1)
    V1(0u, 32, 32, 32), // BLAKE2B + ED25519

    @ProtoNumber(255)
    UNKNOWN(UByte.MAX_VALUE, 0, 0, 0);

    companion object {
        private val map = AttoAlgorithm.entries.associateBy(AttoAlgorithm::code)
        fun from(code: UByte): AttoAlgorithm {
            return map.getOrDefault(code, UNKNOWN)
        }
    }
}