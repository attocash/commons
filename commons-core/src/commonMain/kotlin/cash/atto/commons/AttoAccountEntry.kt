@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsStatic

@JsExportForJs
@Serializable(with = AttoAccountEntrySerializer::class)
data class AttoAccountEntry(
    val hash: AttoHash,
    override val address: AttoAddress,
    override val height: AttoHeight,
    val blockType: AttoBlockType,
    val subjectAddress: AttoAddress,
    val previousBalance: AttoAmount,
    val balance: AttoAmount,
    val timestamp: AttoInstant,
) : HeightSupport,
    AddressSupport {
    companion object {
        @JsStatic
        fun fromJson(value: String): AttoAccountEntry = attoJson.decodeFromString<AttoAccountEntry>(value)
    }

    val algorithm: AttoAlgorithm
        get() = address.algorithm

    val publicKey: AttoPublicKey
        get() = address.publicKey

    val subjectAlgorithm: AttoAlgorithm
        get() = subjectAddress.algorithm

    val subjectPublicKey: AttoPublicKey
        get() = subjectAddress.publicKey

    fun toJson(): String = attoJson.encodeToString(this)
}
