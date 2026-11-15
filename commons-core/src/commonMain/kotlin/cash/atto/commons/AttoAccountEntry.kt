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

    fun toJson(): String = attoJson.encodeToString(this)
}
