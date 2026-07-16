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

fun AttoSendBlock.toReceivable(): AttoReceivable =
    AttoReceivable(
        network = this.network,
        hash = this.hash,
        version = this.version,
        algorithm = this.algorithm,
        publicKey = this.publicKey,
        timestamp = this.timestamp,
        receiverAlgorithm = this.receiverAlgorithm,
        receiverPublicKey = this.receiverPublicKey,
        amount = this.amount,
    )
