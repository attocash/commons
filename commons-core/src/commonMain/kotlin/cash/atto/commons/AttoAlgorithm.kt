package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.Serializable

@JsExportForJs
@Serializable
enum class AttoAlgorithm(
    val code: UByte,
    val privateKeySize: Int,
    val publicKeySize: Int,
    val hashSize: Int,
) {
    V1(0u, 32, 32, 32), // BLAKE2B + ED25519
    ;

    companion object {
        private val map = AttoAlgorithm.entries.associateBy(AttoAlgorithm::code)

        fun from(code: UByte): AttoAlgorithm = map[code] ?: throw IllegalArgumentException("Unsupported algorithm $code code")
    }
}
