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
    @ProtoNumber(255)
    UNKNOWN(UByte.MAX_VALUE, 0, 0, 0),

    @ProtoNumber(0)
    V1(0u, 32, 32, 32), // BLAKE2B + ED25519
    ;

    companion object {
        private val map = AttoAlgorithm.entries.associateBy(AttoAlgorithm::code)

        fun from(code: UByte): AttoAlgorithm = map.getOrDefault(code, UNKNOWN)
    }
}
