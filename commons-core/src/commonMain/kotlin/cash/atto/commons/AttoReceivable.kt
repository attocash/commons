@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsStatic

@JsExportForJs
@Serializable(with = AttoReceivableSerializer::class)
data class AttoReceivable(
    val network: AttoNetwork,
    val hash: AttoHash,
    val version: AttoVersion,
    val address: AttoAddress,
    val timestamp: AttoInstant,
    val receiverAddress: AttoAddress,
    val amount: AttoAmount,
) {
    companion object {
        @JsStatic
        fun fromJson(value: String): AttoReceivable = attoJson.decodeFromString<AttoReceivable>(value)
    }

    val algorithm: AttoAlgorithm
        get() = address.algorithm

    val publicKey: AttoPublicKey
        get() = address.publicKey

    val receiverAlgorithm: AttoAlgorithm
        get() = receiverAddress.algorithm

    val receiverPublicKey: AttoPublicKey
        get() = receiverAddress.publicKey

    fun toJson(): String = attoJson.encodeToString(this)
}

@Deprecated(
    "Moved to AttoSendBlock.toReceivable(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.toReceivable()"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoSendBlock.toReceivable(): AttoReceivable = this.toReceivable()
