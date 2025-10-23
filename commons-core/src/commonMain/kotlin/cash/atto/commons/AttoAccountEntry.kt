package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.Serializable

@JsExportForJs
@Serializable
data class AttoAccountEntry(
    val hash: AttoHash,
    val algorithm: AttoAlgorithm,
    val publicKey: AttoPublicKey,
    override val height: AttoHeight,
    val blockType: AttoBlockType,
    val subjectAlgorithm: AttoAlgorithm,
    val subjectPublicKey: AttoPublicKey,
    val previousBalance: AttoAmount,
    val balance: AttoAmount,
    val timestamp: AttoInstant,
) : HeightSupport, AddressSupport {
    override val address = AttoAddress(algorithm, publicKey)
    val subjectAddress = AttoAddress(subjectAlgorithm, subjectPublicKey)
}
