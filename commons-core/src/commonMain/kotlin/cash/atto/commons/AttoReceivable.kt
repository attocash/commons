@file:OptIn(ExperimentalJsStatic::class)

package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsStatic

@JsExportForJs
@Serializable
data class AttoReceivable(
    val network: AttoNetwork,
    val hash: AttoHash,
    val version: AttoVersion,
    val algorithm: AttoAlgorithm,
    val publicKey: AttoPublicKey,
    val timestamp: AttoInstant,
    val receiverAlgorithm: AttoAlgorithm,
    val receiverPublicKey: AttoPublicKey,
    val amount: AttoAmount,
) {
    companion object {
        @JsStatic
        fun fromJson(value: String): AttoReceivable = attoJson.decodeFromString<AttoReceivable>(value)
    }

    val address by lazy { AttoAddress(algorithm, publicKey) }
    val receiverAddress by lazy { AttoAddress(receiverAlgorithm, receiverPublicKey) }

    fun toJson(): String = attoJson.encodeToString(this)
}

@Deprecated(
    "Moved to AttoSendBlock.toReceivable(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("this.toReceivable()"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoSendBlock.toReceivable(): AttoReceivable = this.toReceivable()
