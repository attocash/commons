@file:OptIn(ExperimentalJsStatic::class)

package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsStatic

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
) : HeightSupport,
    AddressSupport {
    companion object {
        @JsStatic
        fun fromJson(value: String): AttoAccountEntry = attoJson.decodeFromString<AttoAccountEntry>(value)
    }

    override val address = AttoAddress(algorithm, publicKey)
    val subjectAddress = AttoAddress(subjectAlgorithm, subjectPublicKey)

    fun toJson(): String = attoJson.encodeToString(this)
}
